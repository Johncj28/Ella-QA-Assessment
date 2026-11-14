import { ConflictException, NotFoundException } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import { getRepositoryToken } from '@nestjs/typeorm';
import { QueryFailedError } from 'typeorm';
import { User } from './entities/user.entity';
import { UsersService } from './users.service';

const uniqueViolation = () => {
  const err = new QueryFailedError(
    'INSERT',
    [],
    new Error('duplicate key value'),
  );
  (err as unknown as { code: string }).code = '23505';
  return err;
};

describe('UsersService', () => {
  let service: UsersService;
  const repo = {
    create: jest.fn((x: Partial<User>) => x),
    save: jest.fn((x: Partial<User>) => Promise.resolve({ id: 1, ...x })),
    findOne: jest.fn(),
    find: jest.fn(),
    preload: jest.fn(),
  };

  beforeEach(async () => {
    jest.clearAllMocks();
    const moduleRef = await Test.createTestingModule({
      providers: [
        UsersService,
        { provide: getRepositoryToken(User), useValue: repo },
      ],
    }).compile();
    service = moduleRef.get(UsersService);
  });

  it('creates a user', async () => {
    const res = await service.create({
      name: 'Ann',
      email: 'ann@example.test',
    });
    expect(res).toMatchObject({
      statusCode: 201,
      data: { id: 1, email: 'ann@example.test' },
    });
  });

  it.failing(
    'BUG-04: rejects a duplicate email with ConflictException',
    async () => {
      repo.save.mockRejectedValueOnce(uniqueViolation());
      await expect(
        service.create({ name: 'Ann', email: 'ann@example.test' }),
      ).rejects.toBeInstanceOf(ConflictException);
    },
  );

  it('throws NotFound for an unknown id', async () => {
    repo.findOne.mockResolvedValueOnce(null);
    await expect(service.findOne(42)).rejects.toBeInstanceOf(NotFoundException);
  });

  it('update throws NotFound when preload finds nothing', async () => {
    repo.preload.mockResolvedValueOnce(undefined);
    await expect(
      service.update(42, { name: 'x', email: 'x@example.test' }),
    ).rejects.toBeInstanceOf(NotFoundException);
  });

  it('update maps a duplicate email to 409 Conflict', async () => {
    repo.preload.mockResolvedValueOnce({ id: 1 });
    repo.save.mockRejectedValueOnce(uniqueViolation());
    await expect(
      service.update(1, { name: 'x', email: 'taken@example.test' }),
    ).rejects.toThrow(new ConflictException('Email already exists'));
  });
});
