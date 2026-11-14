import { ConflictException, NotFoundException } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import { getRepositoryToken } from '@nestjs/typeorm';
import { QueryFailedError } from 'typeorm';
import { ProductStatus } from 'src/constants';
import { Product } from './entities/product.entity';
import { ProductsService } from './products.service';

const uniqueViolation = () => {
  const err = new QueryFailedError(
    'INSERT',
    [],
    new Error('duplicate key value'),
  );
  (err as unknown as { code: string }).code = '23505';
  return err;
};

describe('ProductsService', () => {
  let service: ProductsService;
  const repo = {
    create: jest.fn((x: Partial<Product>) => x),
    save: jest.fn((x: Partial<Product>) => Promise.resolve({ id: 1, ...x })),
    findOne: jest.fn(),
    find: jest.fn(),
  };

  beforeEach(async () => {
    jest.clearAllMocks();
    const moduleRef = await Test.createTestingModule({
      providers: [
        ProductsService,
        { provide: getRepositoryToken(Product), useValue: repo },
      ],
    }).compile();
    service = moduleRef.get(ProductsService);
  });

  describe('create', () => {
    it('defaults status to FOR_SALE', async () => {
      const res = await service.create({ name: 'Mug', price: 5, quantity: 3 });
      expect(res.statusCode).toBe(201);
      expect(repo.create).toHaveBeenCalledWith(
        expect.objectContaining({ status: ProductStatus.FOR_SALE }),
      );
    });

    it('keeps an explicitly provided status', async () => {
      await service.create({
        name: 'Mug',
        price: 5,
        quantity: 0,
        status: ProductStatus.OUT_OF_STOCK,
      });
      expect(repo.create).toHaveBeenCalledWith(
        expect.objectContaining({ status: ProductStatus.OUT_OF_STOCK }),
      );
    });

    it('maps unique-constraint violations to 409 Conflict', async () => {
      repo.save.mockRejectedValueOnce(uniqueViolation());
      await expect(
        service.create({ name: 'Dup', price: 1, quantity: 1 }),
      ).rejects.toThrow(new ConflictException('Product name already exists'));
    });
  });

  describe('findOne', () => {
    it('throws NotFound for a missing product', async () => {
      repo.findOne.mockResolvedValueOnce(null);
      await expect(service.findOne(5)).rejects.toBeInstanceOf(
        NotFoundException,
      );
    });
  });

  describe('update', () => {
    const existing = (): Product =>
      ({
        id: 1,
        name: 'Lamp',
        price: 100,
        quantity: 4,
        status: ProductStatus.FOR_SALE,
      }) as Product;

    it('throws NotFound for a missing product', async () => {
      repo.findOne.mockResolvedValueOnce(null);
      await expect(
        service.update(9, { name: 'x', price: 1, quantity: 1 }),
      ).rejects.toBeInstanceOf(NotFoundException);
    });

    it('updates name and quantity', async () => {
      repo.findOne.mockResolvedValueOnce(existing());
      const res = await service.update(1, {
        name: 'Desk Lamp',
        price: 100,
        quantity: 9,
      });
      expect(res.data).toMatchObject({ name: 'Desk Lamp', quantity: 9 });
    });

    it.failing(
      'BUG-02: stores the price that was sent, not the quantity',
      async () => {
        repo.findOne.mockResolvedValueOnce(existing());
        const res = await service.update(1, {
          name: 'Lamp',
          price: 250.5,
          quantity: 4,
        });
        expect(res.data!.price).toBe(250.5);
      },
    );
  });
});
