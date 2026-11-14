# Test Strategy: Ella API (e-commerce backend)

| | |
|---|---|
| **Scope** | NestJS REST API: Users, Products, Transactions (+ `GET /health`), PostgreSQL 16, Docker |
| **Out of scope (now)** | UI clients, payments, authentication (not implemented; flagged as a production gap) |
| **Owner** | QA / DevOps |

## 1. Goals and quality risks

The API exists to sell products without corrupting money or inventory, so testing effort is weighted by **business risk** rather than by endpoint count.

| # | Risk | Impact | Likelihood | Primary defence |
|---|---|---|---|---|
| R1 | Inventory wrong after a purchase (wrong arithmetic, race, partial write) | Overselling, lost revenue | **High** (found BUG-01, BUG-09) | Unit tests + API invariant checks + k6 stock-integrity check under load |
| R2 | Price corrupted on update | Selling at the wrong price | **High** (found BUG-02) | Unit tests on field mapping, API round-trip test |
| R3 | Bad input accepted, or a crash into a 500 | Dirty data, noisy alerts, leaked internals | High (BUG-05..08, 11) | Data-driven negative API tests (`Scenario Outline`) |
| R4 | Duplicate or ghost users | Orders attached to the wrong customer | Medium (BUG-04) | API tests on uniqueness |
| R5 | Slow or unbounded endpoints at peak | Checkout timeouts, DB saturation | Medium (BUG-13) | k6 load/stress/soak with SLO thresholds |
| R6 | A deploy breaks the service (build, migration, config) | Outage | Medium (found 15 build/run defects) | CI gates, Docker smoke, `/health`, migration on boot, rollback runbook |

## 2. Test levels

The pyramid stays **wide at the bottom** (fast, cheap, deterministic) and **thin at the top** (slow, realistic).

```
            ┌──────────────┐  k6 performance        nightly / pre-release   (minutes)
            │   Perf       │
          ┌─┴──────────────┴─┐  E2E business flows   every PR                (seconds)
          │   E2E (API)      │
        ┌─┴──────────────────┴─┐  API / negative / contract   every PR       (seconds)
        │   API integration    │
      ┌─┴──────────────────────┴─┐  Unit (services, rules)   every push      (ms)
      │          Unit            │
      └──────────────────────────┘
```

| Level | What it proves | Tooling | Where it runs | In this repo |
|---|---|---|---|---|
| **Static** | Code compiles, style, type safety | `tsc`, ESLint (type-checked rules), Prettier | pre-commit + CI | `npm run lint:ci` (0 warnings allowed) |
| **Unit** | Business rules in isolation: stock maths, status transitions, error mapping. Repositories mocked. | Jest + `@nestjs/testing` | CI on every push | `src/**/*.spec.ts`: 25 tests, open bugs pinned with `it.failing` |
| **API / integration** | Real HTTP → validation pipe → service → real PostgreSQL. Status codes, messages, error shapes, boundaries, negatives. | Java 17, Serenity BDD, Cucumber, RestAssured | CI against a Postgres service container | `qa/automation-framework/.../features/{user,product,transaction}` |
| **E2E (API)** | The critical journey across resources: register → select product → buy → receipt → order history | Same framework | CI | `features/transaction/transaction.feature` (Background + scenarios 1–3) |
| **Performance** | Latency/throughput SLOs, saturation point, stock integrity under concurrency | k6 (open arrival-rate model) + Grafana/InfluxDB | smoke in CI, load nightly, stress/soak before releases | `qa/performance/` |
| **Exploratory** | What scripts don't think of: time-boxed charters ("attack stock consistency", "break the validation pipe") | HTTP client + code reading | each feature, before release | produced the 13 bugs in `BUGS.md` |
| **Deployment** | Image builds, runs as non-root, migrates on boot, becomes healthy | Docker Compose `--wait`, `/health` | CI | `docker` job in `.github/workflows/ci.yml` |

**How test cases are derived:** equivalence partitioning and boundary values (quantity `-1 / 0 / 1 / 1.5 / "abc"`, `stock - 1 / stock / stock + 1`), state transitions (`FOR_SALE ↔ OUT_OF_STOCK`), decision tables (user × product × quantity validity), error guessing (non-numeric ids, duplicates, type confusion), and invariants (`initial stock = current stock + units sold`; one transaction row per successful sale).

## 3. Tooling choices and rationale

| Need | Choice | Why |
|---|---|---|
| API + E2E automation | **Serenity BDD + Cucumber + RestAssured** (Java 17, Maven) | Gherkin scenarios are readable by product owners and developers, so the feature files double as living documentation of expected behaviour. RestAssured is mature for HTTP/JSON assertions. Serenity gives a rich HTML report (each request/response attached) with no extra code. Environments switch with `-Dserenity.environment=dev|qa` in `serenity.conf`. Trade-off: a JVM stack next to a Node service; accepted because the framework is independent of the API's implementation and can target any deployed environment. |
| Test data | JSON files in `test_data/client/` with `{{uid}}` placeholders | Data lives outside the code; unique values make every run independent and parallel-safe on a dirty database (the API has no DELETE). |
| Unit | **Jest** (already configured by Nest) | Fast, mocks built in. |
| Performance | **k6** | Scripts in JS, reviewed in Git, open-model executors, thresholds turn SLOs into a pass/fail exit code for CI. Live dashboards via InfluxDB + Grafana; standalone HTML report per run. JMeter was rejected: GUI-centric and its XML is hard to review. |
| CI | **GitHub Actions** | Repo is on GitHub; service containers give a real Postgres per job. |

## 4. Environment strategy

| Environment | Purpose | Data | Who / when |
|---|---|---|---|
| **Local** | Develop and run any suite | `docker-compose.dev.yml` DB, or the embedded PostgreSQL in `qa/performance/local-env`; throwaway | Dev/QA, on demand |
| **CI (ephemeral)** | Gate every push and PR | Fresh Postgres service container per run; schema via migrations; data created by the tests | Automatic |
| **Staging** | Release-candidate validation: E2E, load, exploratory, migration rehearsal on an anonymised production-like copy | Seeded + anonymised snapshot | On merge to `main` |
| **Production** | Smoke + synthetic monitoring only (read-only checks + `/health`) | Real | After each deploy, continuously |

Principles:
- **Same image everywhere.** Build once, promote the same digest; only environment variables differ (`devops/environment-notes.md`).
- **Tests create their own data through the API**, with unique suffixes. Scenarios are independent and re-runnable.
- **One suite, many targets.** Base URL comes from configuration (`serenity.conf` environments, k6 `-e BASE_URL`), so the same tests run locally, in CI and against staging.
- Load tests never target production and never share a database with functional runs.

## 5. Entry and exit criteria

**Entry (start testing a build):** CI lint/build/unit job green; `/health` returns 200 with `database: up`; migrations applied.

**Exit (release candidate):**
- 100% of non-ignored API/E2E scenarios pass; every `@known-bug` scenario links to a bug ID
- No open **Critical/High** defects (today BUG-01, 02, 04, 09, 10, 13 block release)
- k6 `load` profile meets its thresholds (p95 < 1 s, errors < 1%, checks > 99%) **and** the stock-integrity check reports no defect
- Migration tested up **and down** on a staging snapshot; rollback rehearsed

## 6. Defect management

- Every bug gets a reproducible script or test before it is reported (`BUGS.md`).
- Each open bug also gets a test that asserts the **correct** behaviour: `it.failing` in Jest, and a Cucumber scenario tagged `@known-bug @ignore`. When the fix lands, the Jest test "unexpectedly passes" and turns CI red, and the `@ignore` tag is removed, so the fix becomes a permanent regression test.
- Severity = impact (scale in `BUGS.md`); priority = business urgency. Data-integrity bugs are P1 however rare.

## 7. Deployment risk mitigation

| Risk | Mitigation |
|---|---|
| Code that doesn't build or lint reaches `main` | Required CI checks + branch protection; PR review |
| "Works on my machine" | Same Dockerfile in CI and prod; `npm ci` from the lockfile; Node 20/22 matrix |
| A migration breaks prod or can't be undone | Migrations are reviewed, run in CI on a fresh DB, rehearsed on a staging snapshot. **Expand/contract** for destructive changes. DB snapshot before deploy. |
| Bad release in prod | Rolling or blue/green deploy gated on `/health`; immutable image tags (git SHA), so rollback = redeploy the previous tag (`devops/deployment-runbook.md`) |
| Config/secret mistakes | Required variables fail fast in compose; secrets from a secret manager, never baked into the image (CI checks for `.env` in the image) |
| Performance regression | k6 smoke thresholds in CI; full load on staging before release; latency alerts after deploy |
| Silent post-deploy failures | Post-deploy smoke (read-only subset of the API suite with the production base URL), synthetic `/health` monitor, error-rate and p95 alerts, then canary (5% → 50% → 100%) |

## 8. Metrics to track

Escaped-defect rate (found in prod vs. before), flaky-test rate (target < 1%; quarantine and fix within a sprint), CI duration (target < 10 min per PR), **risk coverage** (every R1–R6 has an automated check), and checkout p95 trend across releases.
