# Runbook: Ella API

| | |
|---|---|
| **Service** | `ella-api`: NestJS REST API (Users, Products, Transactions) |
| **Runtime** | Docker image `ella-api:<git-sha>`, Node 20, listens on `:4000` |
| **Dependencies** | PostgreSQL 16 (schema managed by TypeORM migrations in `src/migrations`) |
| **Health** | `GET /health` → `200 {"status":"ok","checks":{"database":"up"}}` / `503` when the DB is unreachable |
| **Docs** | Swagger UI `/api`, OpenAPI JSON `/api-json` |
| **Related** | [ci-notes.md](ci-notes.md) · [environment-notes.md](environment-notes.md) · [qa/BUGS.md](../qa/BUGS.md) |

> **Status note:** BUG-01, 02, 04, 09 and 10 are release blockers (data integrity). This runbook describes how to operate the service; it doesn't make it production-ready on its own.

---

## 1. Build

```bash
# from the repo root
docker build --build-arg APP_VERSION=$(git rev-parse --short HEAD) -t ella-api:$(git rev-parse --short HEAD) .
```

- Multi-stage build: compiles TypeScript, then copies only `dist/` + production `node_modules` into a non-root runtime image.
- Tag images with the **git SHA**, never only `latest`. Rollback depends on old tags still existing.
- Verify locally before pushing:
  ```bash
  docker run --rm --entrypoint id ella-api:<sha> -u          # must NOT print 0 (root)
  docker image ls ella-api                                   # size sanity check
  ```

Without Docker (e.g. debugging): `npm ci && npm run build && npm run start:prod` (needs the `DB_*` variables).

## 2. Deploy

### 2a. Local / single host (Docker Compose)

```bash
cp .env.example .env                 # first time only; edit values
docker compose up -d --build --wait  # builds, starts db then api, waits until both are healthy
docker compose ps                    # STATUS should show (healthy) for db and api
curl -s localhost:4000/health
```

Migrations run automatically when the API boots (`migrationsRun: true`), so a fresh database gets its schema on first start.

### 2b. Staging / production (managed container platform)

Pre-deploy checklist:
- [ ] CI is green on the commit; image `ella-api:<sha>` exists in the registry
- [ ] Release notes reviewed. **Does this release contain a migration?** If yes, is it backward-compatible (only adds columns/tables)?
- [ ] DB snapshot taken (`aws rds create-db-snapshot --db-instance-identifier ella-<env> --db-snapshot-identifier pre-<sha>`)
- [ ] Current running tag recorded (needed for rollback): `PREVIOUS=<sha>`

Steps:
1. **Run migrations as a one-off task** using the new image, before shifting traffic:
   `node ./node_modules/typeorm/cli.js migration:run -d dist/data-source.js` *(requires shipping a compiled data source; until then, a single instance starting with `migrationsRun: true` does it. Never let N replicas start against a pending migration at once.)*
2. **Roll out** the new image (rolling or blue/green). The platform must wait for `/health = 200` on new tasks before draining old ones.
3. **Verify** (≈10 min):
   ```bash
   curl -sf https://<host>/health | grep '"database":"up"'
   mvn -f qa/automation-framework/pom.xml verify -Dserenity.environment=<env>   # add the host to serenity.conf
   ```
   Watch the dashboard: 5xx rate, p95 latency, restarts. Compare with the previous release.
4. Announce completion with the SHA. Keep the previous image for at least 7 days.

## 3. Roll back

**Decide fast:** roll back if 5xx > 1% for 5 minutes, p95 latency doubles, health checks flap, or a data-integrity issue is reported. Investigate afterwards.

| Situation | Action |
|---|---|
| New code is bad, **no migration** in release | Redeploy the previous tag: `docker compose` → set `APP_VERSION=$PREVIOUS` and `docker compose up -d --wait` (pull `ella-api:$PREVIOUS` instead of building); ECS/Cloud Run → point the service at the previous task definition/revision (one click / `aws ecs update-service --task-definition ella-api:<prev-rev>`). |
| Release had a **backward-compatible** migration (expand only) | Same as above. The old code ignores new columns. Leave the schema; fix forward. |
| Release had a **breaking** migration | 1) Stop traffic or scale the new version to 0. 2) Revert it: `npm run migration:revert` (runs the migration's `down()`; repeat once per migration to undo). 3) Deploy the previous tag. 4) If `down()` can't restore data (dropped column), **restore the pre-deploy snapshot** and accept the data loss window. This is why breaking migrations must not ship in a normal release. |
| Bad config/secret | Revert the variable in the secret store/task definition and restart. No image change needed. |

After any rollback: incident note (what, when, impact, detection, fix), plus a regression test for the cause.

## 4. Common failures and fixes

| Symptom | Likely cause | Diagnose | Fix |
|---|---|---|---|
| Every endpoint returns `500 … relation "product" does not exist` | Migrations didn't run (empty DB, wrong migrations path) | `docker compose logs api \| grep -i migration`; connect and check tables `\dt` | Ensure `dist/migrations/*.js` exists in the image (`docker run --rm --entrypoint ls ella-api:<sha> dist/migrations`); restart the API so `migrationsRun` applies them, or run `npm run migration:run` |
| API container restarts in a loop; log shows `ECONNREFUSED …:5432` / `getaddrinfo ENOTFOUND db` | DB not ready or wrong host/port | `docker compose ps` (is db healthy?); `docker compose exec api env \| grep DB_` | Compose waits on `service_healthy`; check that `DB_HOST=db` and `DB_PORT=5432` inside the container. On cloud: security group / VPC connector between service and DB. |
| `password authentication failed for user …` | `.env`/secret doesn't match the DB, or DB volume initialised with an older password | Compare `DB_PASSWORD` with how the volume was created | Postgres only reads `POSTGRES_PASSWORD` on **first** init. Change it in the DB (`ALTER USER`), or in dev drop the volume: `docker compose down -v` (**deletes data**). |
| `docker compose up` → `required variable DB_DATABASE is missing a value` | No `.env` | `ls .env` | `cp .env.example .env` |
| `Bind for 0.0.0.0:5432 failed: port is already allocated` (dev compose) / `:4000` | A local Postgres or another app holds the port | `netstat -ano \| findstr :5432` (Windows), `lsof -i :5432` (macOS/Linux) | Set `DB_HOST_PORT=5433` (and `DB_PORT=5433` for host-run app) or `API_HOST_PORT=4001` in `.env` |
| `docker build` fails at `npm run build` with `TS…` errors | Type error merged to main | Run `npm run build` locally | Fix the code; CI's build step should have blocked the merge (check branch protection) |
| `Cannot find module 'typeorm'` at container start | A runtime package was put in `devDependencies` | `docker run --rm --entrypoint ls ella-api:<sha> node_modules/typeorm` | Move it to `dependencies` (`npm install --save-prod <pkg>`), rebuild |
| `Error: Cannot find module '/app/dist/main.js'` | Build output layout changed (e.g. root `.ts` file pulled into the build) | `docker run --rm --entrypoint ls ella-api:<sha> -R dist \| head` | Keep `tsconfig.build.json` `include: ["src/**/*"]` |
| `/health` returns **503** | API up, DB down/unreachable/credentials | `/health` body shows `database: down`; DB metrics/logs | Restore DB connectivity; the platform keeps the instance out of rotation until healthy |
| Latency climbs steadily under load; `GET /products` slow and huge | Unbounded list with all transactions embedded (BUG-13) | k6 report; response size of `GET /products` | Short term: scale out, cache. Real fix: pagination + no embedded relations. |
| Stock numbers wrong after busy periods | Lost-update race (BUG-09) + BUG-01 | `SELECT p.id, p.quantity, sum(t.quantity) FROM product p JOIN transaction t ON t."productId"=p.id GROUP BY p.id` | Code fix required (atomic update in a DB transaction); reconcile stock from the transaction table |
| Docker Desktop (Windows) won't start: *"listening on unix://…\Docker\run\dockerInference … The file cannot be accessed by the system"* | Stale AF_UNIX socket file left by a crashed Docker Desktop | `%LOCALAPPDATA%\Docker\log\host\com.docker.backend.exe.log` | Quit Docker Desktop, rename `%LOCALAPPDATA%\Docker\run` (and `%LOCALAPPDATA%\docker-secrets-engine` if named in the error), start Docker Desktop normally (not from a sandboxed shell). Last resort: *Troubleshoot → Reset to factory defaults*. |

Useful commands:
```bash
docker compose logs -f --tail=200 api          # follow app logs
docker compose exec db psql -U postgres -d ella # SQL shell (full stack: DB not published to host)
docker compose exec db pg_dump -U postgres ella > backup.sql
docker inspect --format '{{json .State.Health}}' $(docker compose ps -q api)   # healthcheck history
```

## 5. Monitoring and health

**Health checks (implemented):** `GET /health` does a real `SELECT 1` round-trip and reports DB latency, uptime and `APP_VERSION`. Returns `503` when the DB is unreachable, with no internal error details. Use it for the Docker `HEALTHCHECK` (in the image), the load balancer target-group check, and an external uptime monitor (e.g. every 1 min from 2 regions; alert after 2 consecutive failures).

**Logs:** the app logs to stdout (12-factor). Docker or the platform collects it (`docker compose logs`, CloudWatch Logs via the `awslogs` driver, or Cloud Logging). Recommended next step: structured JSON logs (`nestjs-pino`) with a request id per request, so a client-visible error id can be matched to its server log line. Today, error details leak to clients instead (BUG-11).

**Metrics and alerts** (golden signals):

| Signal | Source | Alert when |
|---|---|---|
| Traffic | LB request count | Sudden drop > 50% vs same time yesterday |
| Errors | LB 5xx count / rate | 5xx > 1% over 5 min |
| Latency | LB target response time | p95 > 300 ms over 10 min (checkout SLO) |
| Saturation | Container CPU/memory, Node event-loop lag, DB CPU, DB connections, disk | CPU > 80% for 10 min; DB connections > 80% of max; DB storage > 80% |
| Availability | `/health` uptime monitor | 2 consecutive failures |
| Restarts | Platform task/container restarts | > 3 in 15 min (crash loop) |
| Business | Purchases per minute; `stock` vs `sum(transactions)` reconciliation query (daily) | Purchases drop to 0 during business hours; any reconciliation mismatch |

Single-host minimum (no cloud tooling): Docker healthcheck + `restart: unless-stopped`, `docker stats`, and an external free uptime checker on `/health`.

---

## 6. Cloud deployment plan (beginner level): AWS

**Goal:** run the same Docker image on AWS with a managed database, HTTPS and automatic restarts, without managing servers.

```
Internet ──HTTPS──▶ Application Load Balancer ──▶ ECS Fargate service (2 tasks, ella-api:<sha>)
                     (public subnets, ACM cert)      (private subnets)
                                                       │  SG: only ALB → :4000
                                                       ▼
                                              Amazon RDS PostgreSQL 16
                                              (private subnets, Multi-AZ, automated backups)
                                                SG: only ECS tasks → :5432
Secrets Manager ──(DB_PASSWORD)──▶ ECS task     CloudWatch ◀── logs + metrics + alarms
ECR ◀── docker push (from GitHub Actions via OIDC)
```

**What each piece does**
- **ECR** (Elastic Container Registry): a private Docker Hub. CI pushes `ella-api:<sha>` here.
- **ECS on Fargate**: runs containers without servers. A *task definition* says which image, CPU/memory (start with 0.5 vCPU / 1 GB), port 4000 and env vars. A *service* keeps 2 copies running and replaces unhealthy ones.
- **RDS PostgreSQL**: managed Postgres with backups, patching and failover. Replaces the `db` container. The app connects with `DB_HOST=<rds-endpoint>`.
- **Application Load Balancer**: public HTTPS entry point (free certificate from ACM). Health check path `/health`; only healthy tasks get traffic.
- **Secrets Manager**: stores `DB_PASSWORD`; ECS injects it as an env var at start. Nothing secret in the image or in Git.
- **CloudWatch**: receives container logs (`awslogs` driver), metrics and the alarms from §5.

**Step by step**
1. Create a VPC (the wizard's "VPC and more": 2 public + 2 private subnets in 2 AZs, 1 NAT gateway).
2. Create the RDS PostgreSQL 16 instance in the private subnets (`db.t4g.micro` for staging), database `ella_staging`, and store the password in Secrets Manager.
3. Create an ECR repository `ella-api`. Push the image (`aws ecr get-login-password | docker login …`, `docker tag`, `docker push`).
4. Create an ECS cluster (Fargate) and a task definition: image `…ecr…/ella-api:<sha>`, port 4000, env `DB_HOST/DB_PORT/DB_USERNAME/DB_DATABASE/NODE_ENV=production/APP_VERSION`, secret `DB_PASSWORD` from Secrets Manager, log driver `awslogs`.
5. Create the ECS service: 2 tasks, private subnets, attach a new ALB (HTTPS 443 → target port 4000, health check `/health`), deployment type *rolling* with **deployment circuit breaker + automatic rollback** enabled.
6. Security groups: ALB accepts 443 from the internet; tasks accept 4000 from the ALB only; RDS accepts 5432 from the tasks only.
7. Point DNS (Route 53) `api.staging.example.com` at the ALB.
8. Automate: add the `deploy-staging` job from `ci-notes.md`. GitHub Actions assumes an AWS role via **OIDC** (no stored access keys), pushes to ECR, and runs `aws ecs update-service --force-new-deployment` with the new task definition.
9. Add CloudWatch alarms (5xx, p95 latency, CPU, RDS connections/storage) → SNS → email/Slack. Enable ECS service auto scaling on CPU (e.g. target 60%, 2–6 tasks).

**Rough staging cost:** Fargate 2 × 0.5 vCPU ≈ $30/mo, RDS t4g.micro ≈ $15/mo, ALB ≈ $20/mo, NAT ≈ $35/mo (the surprise item; use VPC endpoints or one NAT). **Simpler alternatives:** AWS App Runner (point it at the ECR image: it handles the load balancer, HTTPS and scaling) + RDS, or on GCP, Cloud Run + Cloud SQL, which maps one-to-one onto this design with even less setup.
