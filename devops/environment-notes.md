# Environment configuration

Starting point: [`.env.example`](../.env.example). All configuration comes from environment variables (12-factor). The same Docker image runs in every environment; only the values below change.

## 1. Variable reference

### Application and database variables

| Variable | Read by | Required | Default | Description |
|---|---|---:|---|---|
| `PORT` | app (`main.ts`) | No | `4000` | HTTP listen port inside the process/container. |
| `DB_HOST` | app, TypeORM CLI | Yes | none | PostgreSQL host. Compose forces `db` inside the stack. |
| `DB_PORT` | app, TypeORM CLI | Yes | none | PostgreSQL port. Compose forces `5432` inside the stack. |
| `DB_USERNAME` | app, CLI, compose (`POSTGRES_USER`) | Yes | none | Database username. |
| `DB_PASSWORD` | app, CLI, compose (`POSTGRES_PASSWORD`) | Yes (secret) | none | Database password; never commit, never bake into the image. |
| `DB_DATABASE` | app, CLI, compose (`POSTGRES_DB`) | Yes | none | Database name. |
| `NODE_ENV` | Node/Nest libs | No | `production` in image | `development` locally. |
| `APP_VERSION` | `/health`, image tag | No | `dev` / `local` | Git SHA of the build so it is always clear what is deployed. |

### Compose and test variables

| Variable | Read by | Required | Default | Description |
|---|---|---:|---|---|
| `API_HOST_PORT` | compose | No | `4000` | Host port where the API is published. |
| `DB_HOST_PORT` | dev compose | No | `5432` | Host port for the dev database. Set `5433` if a local Postgres already uses `5432`. |
| `BASE_URL` | k6 (`-e BASE_URL=`) | No | `http://localhost:4000` | Target deployment for load tests. |
| `serenity.environment` | Serenity (`-Dserenity.environment=dev\|qa\|staging`) | No | `qa` (from `serenity.conf`) | Selects the API base URL block in `qa/automation-framework/src/test/resources/serenity.conf`. |

> Current gap: the app reads `process.env` directly and does not validate it. A missing `DB_PORT` becomes `Number(undefined) = NaN` and fails later with a confusing connection error. Recommendation: validate config at boot (`ConfigModule.forRoot({ validationSchema })` with Joi/zod), so a misconfigured pod crashes immediately with "DB_PASSWORD is required". Docker Compose already fails fast for the DB variables (`${VAR:?}`).

## 2. Per-environment values

| Variable | Development (local) | Testing (CI) | Staging |
|---|---|---|---|
| Where it comes from | `.env` (copied from `.env.example`) | workflow `env:` block in `ci.yml` | Secret manager (AWS Secrets Manager / SSM, GCP Secret Manager) injected into the task/service; non-secrets as plain env vars |
| `NODE_ENV` | `development` | `test` (unit) / `production` build for API tests | `production` |
| `PORT` | `4000` | `4000` | `4000` (behind a load balancer on 443) |
| `DB_HOST` | `localhost` (dev compose) or `db` (full compose) | `localhost` (GitHub service container) | Managed DB endpoint, e.g. `ella-staging.xxxx.rds.amazonaws.com` |
| `DB_PORT` | `5432` (or `5433` if a local Postgres exists) | `5432` | `5432` |
| `DB_USERNAME` | `postgres` | `postgres` | Least-privilege app user, e.g. `ella_app` (no superuser, no `DROP DATABASE`) |
| `DB_PASSWORD` | `postgres` (throwaway) | `postgres` (ephemeral container) | Strong generated secret, rotated, never in Git/image/logs |
| `DB_DATABASE` | `ella` | `ella_test` | `ella_staging` |
| DB TLS | Off | Off | On (`sslmode=require`); requires an `ssl` option in `TypeOrmModule`, which is not implemented yet |
| `APP_VERSION` | `local` | `${{ github.sha }}` | Git SHA of the promoted image |
| Data | Disposable; reset with `docker compose -f docker-compose.dev.yml down -v` | Fresh empty DB per CI run; schema from migrations; data created by tests | Anonymised production-like snapshot + seeded test accounts; persistent |
| Migrations | Auto-run on app boot (`migrationsRun: true`) or `npm run migration:run` | Auto-run on boot against the empty DB | Run as a separate pre-deploy step after a DB snapshot (see runbook). Auto-run on boot is risky with several replicas starting at once. |
| Logging | Pretty, debug level | Default; API log uploaded as artifact | JSON, `info` level, shipped to central logging (see runbook, Monitoring) |
| Swagger `/api` | Enabled | Enabled | Enabled behind auth/VPN, or disabled; disabled in production |
| Load tests | Allowed (k6 baseline) | k6 smoke only | k6 load/stress scheduled, announced, and isolated to a dedicated DB |

## 3. What changes between environments, and why

1. Where the database is, and how it is reached. Local and CI use a throwaway container; staging uses a managed, backed-up, TLS-only instance on a private network. `DB_HOST`, `DB_PORT`, and `DB_DATABASE` change; the application code does not.
2. How secrets are handled. Development uses well-known throwaway credentials in an untracked `.env`. CI uses ephemeral credentials for a database that lives for minutes. Staging gets real secrets only at runtime from a secret store, scoped to that environment (GitHub environment secrets / IAM role), and rotated.
3. Data lifecycle. Disposable → ephemeral per run → persistent and representative. That is why tests create their own uniquely named data and never assume an empty DB.
4. Risk controls increase toward production. Migrations move from auto-on-boot to an explicit, snapshot-protected step; Swagger is restricted; logs become structured and centralised; and the DB user loses privileges.
5. What stays the same. The image digest, migration files, port inside the container, and test suites remain stable (pointed elsewhere via `-Dserenity.environment` for Serenity and `BASE_URL` for k6). Keeping these identical is what makes "it passed in staging" mean something.

## 4. Example files

### Local development

`.env` for local dev against `docker-compose.dev.yml` on a machine that already runs PostgreSQL:

```dotenv
PORT=4000
DB_HOST=localhost
DB_PORT=5433
DB_HOST_PORT=5433
DB_USERNAME=postgres
DB_PASSWORD=postgres
DB_DATABASE=ella
```

### Staging

Staging (non-secret portion; `DB_PASSWORD` comes from the secret store):

```dotenv
NODE_ENV=production
PORT=4000
DB_HOST=ella-staging.cluster-xxxx.eu-west-1.rds.amazonaws.com
DB_PORT=5432
DB_USERNAME=ella_app
DB_DATABASE=ella_staging
APP_VERSION=<git-sha>
```
