# Ella API: QA / DevOps Assessment Submission

## TL;DR

- **13 application bugs** found and reproduced with evidence, **5 of them release blockers**: purchases *increase* stock, concurrent purchases lose stock updates, updating a product overwrites its price with the quantity, duplicate emails return a fake `201`, and withdrawn products can still be sold.
- **15 build/deploy defects** found and **fixed**. Out of the box the build didn't compile, migrations never ran (every endpoint returned 500), `start:prod` pointed at a missing file, the image baked `.env` into a layer, and `npm test` exited 1.
- **Automation framework** (Java 17, Serenity BDD + Cucumber + RestAssured): 41 scenarios across Users, Products and Transactions, including the **E2E purchase flow** (user → product → transaction → receipt → history) in `transaction.feature`. Known bugs are kept as `@known-bug @ignore` scenarios that assert the correct behaviour. Plus **25 Jest unit tests**.
- **Performance** (k6 + Grafana): a framework with smoke/load/stress/soak profiles for every module, including the critical checkout flow (`tests/transaction/*`). Every run writes a styled HTML report and a JSON summary. Every run ends with a stock-integrity check, which catches BUG-01 on every run.
- **CI** (GitHub Actions): lint → build → unit tests (Node 20/22), then the Serenity suite + k6 smoke against a real Postgres, plus a Docker build/compose smoke job.

## Structure

```
/qa
  test-strategy.md              Task 1  test strategy
  /automation-framework         Task 2  Serenity BDD + Cucumber + RestAssured (Maven project)
  BUGS.md                       Task 3  bug reports
  /performance                  Task 4  k6 framework (checkout: tests/transaction/*), HTML reports, Grafana
/devops
  ci-notes.md                   CI pipeline, container problems found and fixed, CD extension
  environment-notes.md          dev / test / staging variables and what changes
  deployment-runbook.md         build, deploy, rollback, monitoring, failures, AWS plan
/.github/workflows/ci.yml       CI pipeline
src/health/                     GET /health (DB round-trip) + Docker HEALTHCHECK
```

## Approach

1. **Make it run first, and write down everything that got in the way.** A clean clone didn't build or serve a single request. Each blocker was diagnosed to its root cause and fixed, because a reproducible environment comes before trustworthy tests. These are DevOps findings → [`devops/ci-notes.md`](devops/ci-notes.md#problems-found-getting-a-clean-buildrun).
2. **Risk-based exploration.** Money and inventory first, then input validation, then robustness. Black-box HTTP probing combined with a white-box read of every service, DTO, entity and migration, checking invariants ("stock + units sold = initial stock") rather than only status codes.
3. **Evidence before claims.** Every bug was reproduced against the running API (captured responses are in each report) and is pinned by a test that asserts the *correct* behaviour.
4. **Bugs reported, not fixed.** Application logic was left as-is so each report can be verified. The only `src` changes are the health endpoint, the migration, the migrations path, and type-only edits needed for the build and lint to pass.
5. **Known-bug pattern.** Open bugs are `it.failing` in Jest (CI turns red the moment the bug is fixed, forcing the marker's removal) and `@known-bug @ignore` scenarios in Cucumber (remove `@ignore` after the fix and the scenario becomes a regression test).
6. **Readable, environment-independent automation.** Gherkin scenarios document expected behaviour; each scenario creates its own data with unique values, so the suite runs on a dirty database and against any environment (`-Dserenity.environment=dev|qa|staging`).

## Deliverables

| Brief item | Location |
|---|---|
| Test strategy | [`qa/test-strategy.md`](qa/test-strategy.md) |
| Automation framework + execution instructions | [`qa/automation-framework/`](qa/automation-framework/) ([README](qa/automation-framework/README.md)) |
| E2E critical flow (user → product → transaction) | [`qa/automation-framework/src/test/resources/features/transaction/transaction.feature`](qa/automation-framework/src/test/resources/features/transaction/transaction.feature): the Background registers a user and creates a product; scenarios 1–3 buy it, fetch the receipt and check the order history |
| Bug reports | [`qa/BUGS.md`](qa/BUGS.md) |
| Performance scripts (checkout: `POST /transactions`) | [`qa/performance/tests/transaction/`](qa/performance/tests/transaction/) (smoke / load / stress / soak) |
| Performance scenario, assumptions, baseline, analysis | [`qa/performance/README.md`](qa/performance/README.md#results-and-analysis) · raw output [`qa/performance/reports/`](qa/performance/reports/) |
| CI pipeline | [`.github/workflows/ci.yml`](.github/workflows/ci.yml) |
| CI notes + CD extension | [`devops/ci-notes.md`](devops/ci-notes.md) |
| Container troubleshooting | [`devops/ci-notes.md` § Problems found](devops/ci-notes.md#problems-found-getting-a-clean-buildrun) · fixed `Dockerfile`, `docker-compose*.yml`, `.dockerignore` |
| Environment configuration | [`devops/environment-notes.md`](devops/environment-notes.md) |
| Health check | `GET /health` → [`src/health/health.controller.ts`](src/health/health.controller.ts) |
| Monitoring, runbook, rollback, cloud plan (AWS) | [`devops/deployment-runbook.md`](devops/deployment-runbook.md) |
| Unit tests (bonus) | `src/**/*.spec.ts` |

## How to run everything

```bash
# 0. prerequisites: Node 20+, Java 17 + Maven, k6, Docker (or a local PostgreSQL 16)
cp .env.example .env

# 1a. full stack in Docker
docker compose up -d --build --wait
curl http://localhost:4000/health

# 1b. or: DB in Docker, API on the host
docker compose -f docker-compose.dev.yml up -d
npm ci && npm run build && npm run start:prod        # migrations run on boot

# 1c. or: no Docker at all (embedded PostgreSQL on 5433 + API on 4000)
cd qa/performance && npm install && npm run api:start

# 2. static checks + unit tests
npm run lint:ci && npm run build && npm run test:ci

# 3. API + E2E suite (Serenity report: qa/automation-framework/target/site/serenity/index.html)
npm run test:api                  # = mvn -f qa/automation-framework/pom.xml clean verify


# 4. performance (checkout); HTML + JSON reports land in qa/performance/reports/
cd qa/performance
k6 run tests/transaction/smoke.js
k6 run -e RATE_SCALE=0.2 tests/transaction/load.js   # RATE_SCALE=0.2 on a laptop
#    per-module tests, HTML reports and Grafana: see qa/performance/README.md
```

## Verified results (local run, 2026-11-13)

| Check | Result |
|---|---|
| `npm run lint:ci` | ✅ 0 errors / 0 warnings (was failing: CRLF line endings + 6 real errors) |
| `npm run build` | ✅ (was failing: TS2352) |
| `npm run test:ci` | ✅ 25 tests, 4 suites (was: no tests, exit 1) |
| `mvn clean verify` (Serenity/Cucumber) | ✅ 41 scenarios: 39 passed, 2 skipped (`@known-bug @ignore`), 0 failed |
| k6 smoke (user, product, transaction) | ✅ all thresholds; HTML + JSON report per run; integrity check reports BUG-01 |
| k6 load, checkout | see [`qa/performance/README.md`](qa/performance/README.md#results-and-analysis) |
| Compose files | ✅ `docker compose config` valid |

## Assumptions and limitations

- **Docker was not run end-to-end locally.** Docker Desktop on the test machine fails to start (stale Unix-socket files in its own `%LOCALAPPDATA%\Docker\run`; unrelated to this repo, fix in the runbook). The production build (`node dist/main.js`) was run against a real PostgreSQL instead. The Dockerfile and compose files were reviewed and validated with `docker compose config`; the `docker` job in `ci.yml` performs the real build and smoke test.
- **CI hasn't run on GitHub** (nothing pushed). Every command it runs was executed locally.
- A local PostgreSQL already used port 5432, so the test database ran on 5433 (this became fixes #8–9: configurable ports).
- Load numbers come from one laptop running k6, the API and the database together; relative comparisons carry the conclusions.
- The API has no DELETE endpoints, so tests create unique data instead of cleaning up. Use disposable databases.
- Severity ratings assume a production e-commerce context.

## Suggested improvements (priority order)

1. Fix the release blockers: BUG-01/09 together (atomic, transactional stock decrement + `CHECK (quantity >= 0)`), then BUG-02, BUG-04, BUG-10.
2. Fix BUG-13 (stop embedding transaction history, paginate lists, index FKs), rerun `load` to verify the SLO, then `stress` to find the new ceiling.
3. Global exception filter + `ParseIntPipe` + stricter DTOs (BUG-05/06/07/08/11) and `forbidNonWhitelisted`.
4. Validate configuration at boot; run migrations as a dedicated pre-deploy step; TLS to the database in staging/production.
5. Automation: enable parallel Cucumber execution, add JSON-schema contract checks (`json-schema-validator` is already a dependency) against the OpenAPI document, and remove leftover code from the framework's previous project (`EnvConfig` platform/CPS/Mongo getters, encryption headers in `HttpApiUtils`).
6. Structured logging with request IDs, metrics (event-loop lag, DB pool), and the alerts listed in the runbook.
7. CD: push the image by SHA, auto-deploy to staging, run the Serenity suite + k6 `load` there, then promote with a progressive rollout ([`ci-notes.md`](devops/ci-notes.md#extending-ci-toward-continuous-deployment)).
8. Authentication/authorisation: anyone can currently change prices and read every user's email and purchase history.
