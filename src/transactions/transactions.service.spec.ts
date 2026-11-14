import { ConflictException, NotFoundException } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import { getRepositoryToken } from '@nestjs/typeorm';
import { QueryFailedError, Repository } from 'typeorm';
import { ProductStatus } from 'src/constants';
import { Product } from '../products/entities/product.entity';
import { User } from '../users/entities/user.entity';
import { Transaction } from './entities/transaction.entity';
import { TransactionsService } from './transactions.service';

type RepoMock<T extends object> = {
  [K in keyof Repository<T>]?: jest.Mock;
};

const repoMock = <T extends object>(): RepoMock<T> => ({
  findOne: jest.fn(),
  find: jest.fn(),
  create: jest.fn((x: T) => x),
  save: jest.fn((x: T) => Promise.resolve({ id: 1, ...x })),
});

describe('TransactionsService', () => {
  let service: TransactionsService;
  let transactions: RepoMock<Transaction>;
  let users: RepoMock<User>;
  let products: RepoMock<Product>;

  const user = { id: 7, name: 'Ann', email: 'ann@example.test' } as User;
  const productWith = (quantity: number, status = ProductStatus.FOR_SALE) =>
    ({ id: 3, name: 'Lamp', price: 10, quantity, status }) as Product;

  beforeEach(async () => {
    transactions = repoMock<Transaction>();
    users = repoMock<User>();
    products = repoMock<Product>();

    const moduleRef = await Test.createTestingModule({
      providers: [
        TransactionsService,
        { provide: getRepositoryToken(Transaction), useValue: transactions },
        { provide: getRepositoryToken(User), useValue: users },
        { provide: getRepositoryToken(Product), useValue: products },
      ],
    }).compile();

    service = moduleRef.get(TransactionsService);
  });

  describe('create', () => {
    it('persists a transaction for an existing user and in-stock product', async () => {
      users.findOne!.mockResolvedValue(user);
      products.findOne!.mockResolvedValue(productWith(10));

      const res = await service.create({
        userId: 7,
        productId: 3,
        quantity: 2,
      });

      expect(res.statusCode).toBe(201);
      expect(transactions.save).toHaveBeenCalledWith(
        expect.objectContaining({ user, quantity: 2 }),
      );
    });

    it('throws NotFound when the user does not exist (and never touches stock)', async () => {
      users.findOne!.mockResolvedValue(null);

      await expect(
        service.create({ userId: 99, productId: 3, quantity: 1 }),
      ).rejects.toThrow(new NotFoundException('User with ID 99 not found'));
      expect(products.save).not.toHaveBeenCalled();
      expect(transactions.save).not.toHaveBeenCalled();
    });

    it('throws NotFound when the product does not exist', async () => {
      users.findOne!.mockResolvedValue(user);
      products.findOne!.mockResolvedValue(null);

      await expect(
        service.create({ userId: 7, productId: 404, quantity: 1 }),
      ).rejects.toBeInstanceOf(NotFoundException);
    });

    it('throws Conflict when quantity exceeds stock', async () => {
      users.findOne!.mockResolvedValue(user);
      products.findOne!.mockResolvedValue(productWith(2));

      await expect(
        service.create({ userId: 7, productId: 3, quantity: 3 }),
      ).rejects.toBeInstanceOf(ConflictException);
      expect(transactions.save).not.toHaveBeenCalled();
    });

    it('allows buying exactly the remaining stock (boundary)', async () => {
      users.findOne!.mockResolvedValue(user);
      products.findOne!.mockResolvedValue(productWith(2));

      await expect(
        service.create({ userId: 7, productId: 3, quantity: 2 }),
      ).resolves.toMatchObject({ statusCode: 201 });
    });

    it('maps database errors to Conflict', async () => {
      users.findOne!.mockResolvedValue(user);
      products.findOne!.mockResolvedValue(productWith(5));
      transactions.save!.mockRejectedValue(
        new QueryFailedError('INSERT', [], new Error('fk violation')),
      );

      await expect(
        service.create({ userId: 7, productId: 3, quantity: 1 }),
      ).rejects.toBeInstanceOf(ConflictException);
    });

    // ---- Known defects: these assert the CORRECT behaviour and are expected to fail
    // ---- until the bug is fixed (then Jest reports them as failing -> remove `.failing`).

    it.failing(
      'BUG-01: decrements product stock by the purchased quantity',
      async () => {
        users.findOne!.mockResolvedValue(user);
        products.findOne!.mockResolvedValue(productWith(10));

        await service.create({ userId: 7, productId: 3, quantity: 3 });

        expect(products.save).toHaveBeenCalledWith(
          expect.objectContaining({ quantity: 7 }),
        );
      },
    );

    it.failing(
      'BUG-01: marks the product OUT_OF_STOCK when the last unit is sold',
      async () => {
        users.findOne!.mockResolvedValue(user);
        products.findOne!.mockResolvedValue(productWith(1));

        await service.create({ userId: 7, productId: 3, quantity: 1 });

        expect(products.save).toHaveBeenCalledWith(
          expect.objectContaining({
            quantity: 0,
            status: ProductStatus.OUT_OF_STOCK,
          }),
        );
      },
    );

    it.failing(
      'BUG-10: rejects purchase of a product whose status is OUT_OF_STOCK',
      async () => {
        users.findOne!.mockResolvedValue(user);
        products.findOne!.mockResolvedValue(
          productWith(50, ProductStatus.OUT_OF_STOCK),
        );

        await expect(
          service.create({ userId: 7, productId: 3, quantity: 1 }),
        ).rejects.toBeInstanceOf(ConflictException);
      },
    );
  });

  describe('findOne', () => {
    it('throws NotFound for an unknown id', async () => {
      transactions.findOne!.mockResolvedValue(null);
      await expect(service.findOne(123)).rejects.toBeInstanceOf(
        NotFoundException,
      );
    });

    it('loads user and product relations', async () => {
      transactions.findOne!.mockResolvedValue({ id: 1 });
      await service.findOne(1);
      expect(transactions.findOne).toHaveBeenCalledWith({
        where: { id: 1 },
        relations: ['user', 'product'],
      });
    });
  });
});
