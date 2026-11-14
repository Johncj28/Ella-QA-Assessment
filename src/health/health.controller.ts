import {
  Controller,
  Get,
  Header,
  ServiceUnavailableException,
} from '@nestjs/common';
import { InjectDataSource } from '@nestjs/typeorm';
import { DataSource } from 'typeorm';

export interface HealthReport {
  status: 'ok' | 'error';
  uptimeSeconds: number;
  timestamp: string;
  version: string;
  checks: { database: 'up' | 'down'; databaseLatencyMs?: number };
}

/**
 * Liveness + readiness probe for load balancers, Docker HEALTHCHECK and uptime monitors.
 *
 * Returns 200 only when the process is up AND the database answers a round-trip query;
 * otherwise 503, so orchestrators stop routing traffic to an instance that cannot serve it.
 * The response never includes connection strings or error internals.
 */
@Controller('health')
export class HealthController {
  constructor(@InjectDataSource() private readonly dataSource: DataSource) {}

  @Get()
  @Header('Cache-Control', 'no-store')
  async check(): Promise<HealthReport> {
    const report: HealthReport = {
      status: 'ok',
      uptimeSeconds: Math.round(process.uptime()),
      timestamp: new Date().toISOString(),
      version: process.env.APP_VERSION ?? 'dev',
      checks: { database: 'up' },
    };

    const started = Date.now();
    try {
      await this.dataSource.query('SELECT 1');
      report.checks.databaseLatencyMs = Date.now() - started;
      return report;
    } catch {
      report.status = 'error';
      report.checks.database = 'down';
      throw new ServiceUnavailableException(report);
    }
  }
}
