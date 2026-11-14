import { ServiceUnavailableException } from '@nestjs/common';
import { DataSource } from 'typeorm';
import { HealthController } from './health.controller';

describe('HealthController', () => {
  it('reports ok when the database answers', async () => {
    const ds = { query: jest.fn().mockResolvedValue([{ '?column?': 1 }]) };
    const res = await new HealthController(ds as unknown as DataSource).check();
    expect(res).toMatchObject({ status: 'ok', checks: { database: 'up' } });
    expect(ds.query).toHaveBeenCalledWith('SELECT 1');
  });

  it('throws 503 without leaking error details when the database is down', async () => {
    const ds = {
      query: jest
        .fn()
        .mockRejectedValue(new Error('password authentication failed')),
    };
    const controller = new HealthController(ds as unknown as DataSource);

    const err = await controller.check().catch((e: unknown) => e);
    expect(err).toBeInstanceOf(ServiceUnavailableException);
    const body = JSON.stringify(
      (err as ServiceUnavailableException).getResponse(),
    );
    expect(body).toContain('"database":"down"');
    expect(body).not.toContain('password');
  });
});
