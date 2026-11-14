# CI/CD Notes

Pipeline: [`.github/workflows/ci.yml`](../.github/workflows/ci.yml), triggered on **every push (any branch), every pull request**, and manually (`workflow_dispatch`).

## Pipeline design

```
push / PR
   │
   ▼
┌──────────────────────────────────────┐
│ build-and-test  (Node 20 + 22 matrix)│   ← the brief's required gate
│  npm ci → lint:ci → build → test:ci  │
└──────────────┬───────────────────────┘
               │ needs (only runs if green)
       ┌───────┴─────────────┐
       ▼                     ▼
┌──────────────────┐   ┌───────────────────────────┐
│ api-tests        │   │ docker                    │
│ Postgres service │   │ compose up --build --wait │
│ start built API  │   │ curl /health + POST smoke │
│ Serenity/Cucumber│   │ non-root + no .env checks │
│ k6 smoke (SLOs)  │   │ logs on failure, down -v  │
└──────────────────┘   └───────────────────────────┘
```

| Step | Command | Fails the build when |
|---|---|---|
| Install | `npm ci` | lockfile and `package.json` disagree, or a package fails to install |
| Lint | `npm run lint:ci` = `eslint … --max-warnings=0` (**no `--fix`**) | any lint error *or warning*. The repo's `npm run lint` uses `--fix`, which in CI would "fix" the workspace and hide the problem. |
| Build | `npm run build` | any TypeScript error |
| Unit tests | `npm run test:ci` = `jest --ci --coverage` | any failing test, or an `it.failing` known-bug test that starts passing (bug fixed → remove marker) |
| API/E2E | `mvn clean verify -Dserenity.environment=qa` (Serenity BDD + Cucumber + RestAssured, Java 17) against the built app + real Postgres 16 | any failed scenario (Failsafe `verify` fails the build); `@known-bug @ignore` scenarios are skipped until the bug is fixed |
| k6 smoke | `k6 run tests/transaction/smoke.js` in `qa/performance` (checkout flow; HTML + JSON report in `qa/performance/reports/`) | any threshold breached (p95 < 500 ms, errors < 1%, checks > 99%) |
| Docker | `docker compose up -d --build --wait` | image doesn't build, container unhealthy within 120 s, runs as root, `.env` found in image |

Every step relies on the default `bash -e` behaviour of GitHub Actions: a non-zero exit fails the step, the job, and every job that `needs` it. Reports (Serenity HTML, Failsafe XML, Cucumber HTML, coverage, k6 summary, API log) are uploaded as artifacts **even on failure** (`if: always()`), so a red build always comes with evidence.

Other choices: `concurrency` cancels superseded runs on the same branch; `permissions: contents: read` (least privilege); npm cache keyed on both lockfiles; job `timeout-minutes` so a hung test can't burn 6 hours of runner time.

### State on the current code

| Check | Result (local run, Windows, Node 22) |
|---|---|
| `npm ci` | ✅ |
| `npm run lint:ci` | ✅ 0 errors, 0 warnings (was **805 errors + 1 warning**; see problems 1–2 below) |
| `npm run build` | ✅ (was **failing**; problem 3) |
| `npm run test:ci` | ✅ 25 tests (18 passing + 7 `it.failing` known bugs); was **"No tests found" → exit 1** |
| Serenity/Cucumber suite (`mvn clean verify`) | ✅ 41 scenarios: 39 passed, 2 skipped (`@known-bug @ignore`), 0 failed |
| k6 smoke (`tests/transaction/smoke.js`) | ✅ all thresholds (p95 75 ms, 0% errors, 100% checks); integrity check reports BUG-01 |
| `docker compose up --build` | ⚠️ **Not executed locally**: Docker Desktop on the test machine fails to start (Windows AF_UNIX socket error in Docker's own `run/` directory, unrelated to this repo). The compose files were validated with `docker compose config`, and the Dockerfile was reviewed line by line. The `docker` CI job is where the build and runtime are verified. |

---

## Problems found getting a clean build/run

How each one was diagnosed and fixed. These are **infrastructure** defects; application logic bugs are in [`qa/BUGS.md`](../qa/BUGS.md).

| # | Symptom | Diagnosis | Fix |
|---|---|---|---|
| 1 | `npm run lint` → **799 `Delete ␍` errors** on Windows | `git ls-files --eol` shows `i/lf w/crlf`: files are LF in Git, but `core.autocrlf=true` converts them to CRLF on checkout, and Prettier expects LF. Linux CI wouldn't see it; Windows contributors always would. | Added **`.gitattributes`** `* text=auto eol=lf` |
| 2 | 6 real ESLint errors + 1 warning | `no-unsafe-member-access` on `(error as any).code`, unused `Put` import, floating `bootstrap()` promise | Typed the error (`QueryFailedError & { code?: string }`), removed the import, `void bootstrap()`. No behaviour change. |
| 3 | **`npm run build` fails**, so `docker build` fails at `RUN npm run build` | `TS2352` in `users.service.ts`: `{...dto, id: 0, transactions: []} as User` is missing `createdAt/updatedAt` | Added the missing `createdAt`/`updatedAt` to the literal so it type-checks. The faulty logic behind it (BUG-04: a fake `201` with `id: 0`) is intentionally **left in place and reported**. |
| 4 | App starts, but every request → **500 `relation "product" does not exist`** | (a) **No migrations are committed** (`src/migrations/` doesn't exist), and `synchronize: false`. (b) Even after `npm run migration:generate`, the app still had no tables: `migrations: ['dist/migrations/*.js']`, but `ls dist` shows they compile to **`dist/src/migrations`**. | Committed the initial migration `src/migrations/1731196800000-InitialSchema.ts`. Made the glob `join(__dirname, 'migrations', '*.{js,ts}')` (independent of CWD). Root cause of the path shift: fix 5. |
| 5 | **`npm run start:prod` → `Cannot find module dist/main`**, while the Dockerfile/compose use `dist/src/main.js` | Root-level `data-source.ts` and an **empty `typeorm.config.ts`** are inside the TS program, so `rootDir` becomes the repo root and output moves to `dist/src/…`. Two "fixes" had already diverged (`start:prod` vs Docker `CMD`). | `tsconfig.build.json` now `include: ["src/**/*"]`, giving `dist/main.js` and `dist/migrations/*.js` (the layout everything expects). Deleted the empty `typeorm.config.ts`. One `CMD`, no compose override. |
| 6 | Runtime image works only because it installs **all devDependencies** | `typeorm` is listed in **devDependencies**, but `grep require("typeorm") dist/` shows 4 runtime modules need it. The original Dockerfile hid this with `npm install` (dev deps included) in the final stage, plus an extra `npm install typeorm` in the builder. | Moved `typeorm` to `dependencies`. Final stage uses `npm ci --omit=dev`. |
| 7 | **`COPY .env ./` fails on a fresh clone** (`.env` is git-ignored), and when it succeeds it **bakes credentials into an image layer** | Read the Dockerfile. `.dockerignore` didn't exclude `.env`. | Removed `COPY .env`. Config is injected at runtime (`env_file` / orchestrator secrets). `.dockerignore` excludes `.env*`. CI asserts no `.env` exists in the image. |
| 8 | `docker compose up` fails with **`port is already allocated` / bind error on 5432** on a machine with local PostgreSQL | `Get-NetTCPConnection -LocalPort 5432` showed a local `postgres` already listening, the same thing that blocked my test DB. | Full-stack compose **no longer publishes the DB port** (the API reaches it over the compose network). Dev compose uses `${DB_HOST_PORT:-5432}`. API host port is `${API_HOST_PORT:-4000}`. |
| 9 | API container can't reach the DB if `.env` has a non-default `DB_PORT` | `.env` is shared by host and container, but compose overrode only `DB_HOST=db` and not `DB_PORT` | Compose sets `DB_HOST: db` **and** `DB_PORT: 5432` for the container. Verified with `docker compose config`. |
| 10 | Starting dev DB then full stack: **`container name "/shop-db" is already in use`** | Both compose files hard-code `container_name: shop-db` (and the API `shop-api`). Fixed names also prevent `--scale` and parallel CI stacks. | Removed `container_name`; Compose names containers per project. |
| 11 | `docker compose -f docker-compose.dev.yml` warns *"`version` is obsolete"* | Compose v2 ignores the key | Removed `version: '3.9'` |
| 12 | Missing `.env` gives a confusing failure (empty `POSTGRES_PASSWORD` → postgres refuses to init) | Variables silently interpolated as empty | `${DB_PASSWORD:?set DB_PASSWORD in .env}`: Compose now fails immediately with that message (verified). |
| 13 | No way for Docker/orchestrator to know the API is up; `depends_on` only waits for the DB | No app healthcheck | Added `GET /health` (DB round-trip, 503 when DB down) + `HEALTHCHECK` in the Dockerfile. CI uses `compose up --wait`. |
| 14 | Image hygiene | Runs as root; `npm install` (non-reproducible); `--legacy-peer-deps` masking peer issues (`npm ls` shows none, so it isn't needed); no `NODE_ENV` | `USER node`; `npm ci`; dropped `--legacy-peer-deps`; `NODE_ENV=production`; 4-stage build; `APP_VERSION` build arg reported by `/health`. |
| 15 | `npm test` exits 1 ("No tests found"), so any CI would be red on day one | No `*.spec.ts` exists; `test:e2e` points to a missing `test/jest-e2e.json` | Added unit tests for all services + health. Jest ignores `qa/` and `dist/`. (`test:e2e` removed; the Serenity/Cucumber suite, including the end-to-end purchase flow in `transaction.feature`, runs with `npm run test:api`.) |

Also noted, not changed: `prisma` is an unused devDependency; `@nestjs/mapped-types: "*"` is unpinned (the lockfile protects us, but `*` invites a breaking major on the next `npm install`).

---

## Extending CI toward continuous deployment

The current pipeline is **continuous integration**. The path to **continuous deployment**, in order:

1. **Build once, publish an immutable image.** On merge to `main`, build the Docker image once and push it to a registry (GHCR / Amazon ECR) tagged with the **git SHA** (plus `main` as a moving tag). Every later stage deploys *that digest*, never a rebuild.
2. **Supply-chain gates** on that image: `npm audit --omit=dev --audit-level=high`, an image scan (Trivy/Grype) failing on HIGH/CRITICAL, an SBOM, and Dependabot for actions and npm.
3. **Deploy to staging automatically** (GitHub `environment: staging`): run migrations as a one-off task *before* switching traffic, roll out the new task, wait for `/health`, then run the Serenity/Cucumber suite (`-Dserenity.environment=staging`) and the k6 **load** profile against staging (`BASE_URL=https://staging…`).
4. **Promote to production** with the same image digest. Start with a manual approval (GitHub environment protection rule); remove it once staging gates have proven reliable (true CD).
5. **Progressive rollout + automatic rollback.** Blue/green or canary (ECS/CodeDeploy, Cloud Run traffic split). Watch error rate and p95 latency for N minutes; breach → shift traffic back to the previous revision automatically. Post-deploy read-only smoke test against production.
6. **Database safety:** snapshot before migrating; only backward-compatible (expand/contract) migrations in a normal deploy, so the previous image can still run against the new schema and rollback is just "redeploy previous tag".
7. **Notifications and traceability:** Slack/Teams message with SHA, changelog and links to reports; deployment recorded in GitHub Deployments.

Sketch of the extra job:

```yaml
  deploy-staging:
    needs: [api-tests, docker]
    if: github.ref == 'refs/heads/main' && github.event_name == 'push'
    environment: staging            # secrets scoped here; optional approvers
    permissions: { contents: read, packages: write, id-token: write }  # OIDC to cloud, no long-lived keys
    steps:
      - uses: actions/checkout@v4
      - uses: docker/login-action@v3
        with: { registry: ghcr.io, username: ${{ github.actor }}, password: ${{ secrets.GITHUB_TOKEN }} }
      - run: |
          docker build --build-arg APP_VERSION=${{ github.sha }} -t ghcr.io/${{ github.repository }}:${{ github.sha }} .
          docker push ghcr.io/${{ github.repository }}:${{ github.sha }}
      - run: ./devops/deploy.sh staging ${{ github.sha }}   # e.g. aws ecs update-service / gcloud run deploy
      - run: mvn -B -f qa/automation-framework/pom.xml verify -Dserenity.environment=staging
```
