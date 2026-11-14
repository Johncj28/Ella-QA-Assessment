# Ella API bug reports

| Field | Value |
| --- | --- |
| **Build under test** | `ellatech-eth/QA-Assessment` @ `78b919e` (main) |
| **Environment** | Windows 11, Node 22.14, PostgreSQL 16.15, API on `http://localhost:4000` |
| **Method** | Exploratory testing (black-box via HTTP) + white-box code review of every service, DTO, entity and infra file |
| **Evidence** | Every bug below was reproduced against the running API; the captured request/response is included in each report. Each issue is also pinned by an `it.failing` unit test (`src/**/*.spec.ts`) and, for the purchase flow, by a `@known-bug @ignore` Cucumber scenario in `automation-framework/`. |

## 1. Severity scale

| Severity | Meaning |
| --- | --- |
| **Critical** | Corrupts core business data or money flow; no workaround; blocks release |
| **High** | Wrong data or misleading result in a main flow; workaround is awkward or none |
| **Medium** | Incorrect behaviour with limited blast radius, or a robustness/security gap |
| **Low** | Contract/consistency issue, cosmetic, or best-practice deviation |

## 2. Bug summary

| ID | Title | Severity | Area |
| --- | --- | --- | --- |
| [BUG-01](#bug-01) | Purchasing a product **increases** its stock | Critical | Transactions |
| [BUG-09](#bug-09) | Concurrent purchases lose stock updates (no locking, non-atomic write) | Critical | Transactions |
| [BUG-02](#bug-02) | Updating a product overwrites its **price with the quantity** | Critical | Products |
| [BUG-04](#bug-04) | Duplicate email returns `201 Created` with a fake user (`id: 0`) | High | Users |
| [BUG-10](#bug-10) | Product `status` is neither enforced on purchase nor kept in sync with stock | High | Products / Transactions |
| [BUG-13](#bug-13) | Read endpoints are unbounded and embed every transaction (saturates the API under load) | High (performance) | All modules |
| [BUG-03](#bug-03) | `PUT` endpoints reject partial updates (Create DTO used for update) | Medium | Products / Users |
| [BUG-05](#bug-05) | Email format is not validated (any string or even a number is accepted) | Medium | Users |
| [BUG-06](#bug-06) | Negative prices, empty product names and blank user names are accepted | Medium | Products / Users |
| [BUG-07](#bug-07) | Fractional / out-of-range quantity gives `500` instead of `400` | Medium | Products |
| [BUG-08](#bug-08) | Malformed path ids (`abc`, `99999999999`) give `500` instead of `400` | Medium | All modules |
| [BUG-11](#bug-11) | Raw database error messages are returned to clients | Medium (security) | All modules |
| [BUG-12](#bug-12) | `price` is returned as a string on reads but a number on create | Low | Products |

Infrastructure and build defects found while getting a clean build/run (TypeScript compile error, migrations never running, `typeorm` in devDependencies, `.env` baked into the image…) are documented separately in [`../devops/ci-notes.md`](../devops/ci-notes.md#problems-found-getting-a-clean-buildrun), because they were **fixed** as part of the DevOps track. The application bugs below were deliberately **not fixed**, so they can be verified.

---

## 3. Detailed findings

<a id="bug-01"></a>
## BUG-01 — Purchasing a product increases its stock instead of decreasing it

| Field | Value |
| --- | --- |
| Severity | **Critical** |
| Priority | P1 (release blocker) |
| Endpoint | `POST /transactions` |
| Component | `src/transactions/transactions.service.ts` → `create()` |

**Description**
Every successful purchase adds the purchased quantity to the product's stock. Inventory grows with every sale, so stock figures are meaningless, the product can never sell out, and the `OUT_OF_STOCK` transition is unreachable.

**Steps to reproduce**
1. `POST /users` `{"name":"Buyer","email":"buyer@example.com"}` → note `data.id` (e.g. `1`)
2. `POST /products` `{"name":"Lamp","price":19.99,"quantity":10}` → note `data.id` (e.g. `1`)
3. `POST /transactions` `{"userId":1,"productId":1,"quantity":3}` → `201`
4. `GET /products/1`

**Expected:** `quantity` = **7**. When stock reaches 0, `status` = `OUT_OF_STOCK`.
**Actual:** `quantity` = **13**. Buying the only unit of a 1-unit product leaves `quantity: 2, status: "FOR_SALE"`.

**Evidence** (captured response)
```json
GET /products/:id  after buying 3 of 10
HTTP 200
{ "data": { "quantity": 13, "status": "FOR_SALE", "transactions": [ { "quantity": 3 } ] } }
```
k6 load run: 301 purchases → stock change observed **+301** (should be −301).

**Root cause**
```ts
// transactions.service.ts
product.quantity += dto.quantity;      // should be -=
if (product.quantity === 0) { product.status = ProductStatus.OUT_OF_STOCK; }
```

**Suggested fix:** `product.quantity -= dto.quantity`, combined with the atomic update from BUG-09 (`<= 0` guard).
**Tests:** `features/transaction/transaction.feature` › *BUG-01. Buying reduces the product's stock*, *Selling the last unit marks the product OUT_OF_STOCK* (all `@known-bug @ignore`); `transactions.service.spec.ts` › `BUG-01`; k6 `teardown()` prints `DEFECT: purchases INCREASED stock` on every run.

---

<a id="bug-09"></a>
## BUG-09 — Concurrent purchases lose stock updates (race condition, no database transaction)

| Field | Value |
| --- | --- |
| Severity | **Critical** |
| Priority | P1 |
| Endpoint | `POST /transactions` |
| Component | `transactions.service.ts` → `create()` |

**Description**
Stock is updated with an application-level *read → modify → write*, with no row lock, no database transaction and no atomic `UPDATE`. Under concurrency, requests read the same stale quantity and overwrite each other's writes (**lost update**). Once BUG-01 is fixed, the same race lets the shop **sell more units than it has** (oversell), because two buyers can both pass the `dto.quantity > product.quantity` check against the same snapshot.

The insert and the stock update are also two separate statements outside a transaction: if the second fails, a sale is recorded without its stock movement.

**Steps to reproduce**
1. Create a product with `quantity: 100` and 4 users.
2. Fire 20 `POST /transactions` (`quantity: 1`) **in parallel** (e.g. `Promise.all` in a Node script, or k6 with several VUs).
3. `GET /products/:id`.

**Expected:** 20 transaction rows and the stock changed by exactly 20 (−20 once BUG-01 is fixed).
**Actual:**
```text
concurrent requests     : 20
HTTP 201 responses      : 20
transaction rows stored : 20
initial stock           : 100
final stock             : 103
stock delta applied     : 3   (should equal 20)
```
17 of 20 stock updates were silently lost. Being a race, the exact number varies per run (another run: 15 of 20 lost), but it has never come out correct. Under the k6 load test, **64–78%** of stock updates were lost (measured with the earlier single-file k6 script; the current k6 framework reports the same check in `teardown()`, see `performance/README.md`).

**Root cause:** `findOne()` → JS arithmetic → `save()` with no isolation.

**Suggested fix:** do it in one DB transaction with an atomic, guarded decrement:
```ts
await this.dataSource.transaction(async (m) => {
  const res = await m.createQueryBuilder().update(Product)
    .set({ quantity: () => `quantity - ${dto.quantity}` })
    .where('id = :id AND quantity >= :q AND status = :s', { id: dto.productId, q: dto.quantity, s: 'FOR_SALE' })
    .returning('*').execute();
  if (!res.affected) throw new ConflictException('Insufficient stock');
  await m.save(Transaction, { user, product: res.raw[0], quantity: dto.quantity });
});
```
plus a DB constraint `CHECK (quantity >= 0)` as a last line of defence.
**Tests:** k6 `teardown()` stock-integrity check compares units sold with stock moved after every transaction run.

---

<a id="bug-02"></a>
## BUG-02 — Updating a product overwrites its price with the quantity

| Field | Value |
| --- | --- |
| Severity | **Critical** (silent pricing corruption) |
| Priority | P1 |
| Endpoint | `PUT /products/:id` |
| Component | `products.service.ts` → `update()` |

**Steps to reproduce**
1. `POST /products` `{"name":"Desk","price":250.5,"quantity":4}`
2. `PUT /products/:id` `{"name":"Desk","price":250.5,"quantity":4}` (any update, even re-sending the same values)

**Expected:** `price` stays `250.5`.
**Actual:** `price` becomes `4`. A 250.50 item is now sold for 4.00.
```json
HTTP 200
{ "message": "Product updated successfully", "data": { "price": 4, "quantity": 4 } }
```

**Root cause:** copy/paste error:
```ts
product.price = dto.quantity ?? product.price;   // should be dto.price
```
**Suggested fix:** `product.price = dto.price ?? product.price;`, plus a unit test on the mapping of every field.
**Tests:** `products.service.spec.ts` › `BUG-02`.

---

<a id="bug-04"></a>
## BUG-04 — Duplicate email returns `201 Created` with a fabricated user (`id: 0`)

| Field | Value |
| --- | --- |
| Severity | **High** |
| Priority | P1 |
| Endpoint | `POST /users` |
| Component | `users.service.ts` → `create()` |

**Steps to reproduce**
1. `POST /users` `{"name":"Original","email":"dup@example.com"}` → `201`
2. `POST /users` `{"name":"Impostor","email":"dup@example.com"}`

**Expected:** `409 Conflict` "Email already exists" (consistent with `PUT /users/:id`, which already does this).
**Actual:** `201 Created`, "User created successfully", with the *request* echoed back and `id: 0`:
```json
HTTP 201
{ "statusCode": 201, "message": "User created successfully",
  "data": { "name": "Impostor", "email": "dup@example.com", "id": 0, "transactions": [],
            "createdAt": "<now>", "updatedAt": "<now>" } }
```
The database still contains only "Original". `GET /users/0` → `404`. A client that stores the returned id will later fail (e.g. `POST /transactions {userId: 0}` → 404) with no way to understand why.

**Root cause:** the unique-violation (`23505`) branch deliberately returns a fake success instead of throwing:
```ts
if (error instanceof QueryFailedError && error.code === '23505') {
  return { statusCode: HttpStatus.CREATED, message: 'User created successfully', data: { ...dto, id: 0 } };
}
```
This code also failed to compile (TS2352: the literal lacked `createdAt`/`updatedAt`); the build fix only added those fields, so the faulty behaviour is unchanged. See `devops/ci-notes.md`.
**Suggested fix:** `throw new ConflictException('Email already exists')`.
**Tests:** `users.service.spec.ts` › `BUG-04`.

---

<a id="bug-10"></a>
## BUG-10 — Product status is not enforced on purchase nor kept in sync with stock

| Field | Value |
| --- | --- |
| Severity | **High** |
| Endpoints | `POST /transactions`, `POST/PUT /products` |

**Steps to reproduce**
1. `POST /products` `{"name":"Hidden","price":9,"quantity":50,"status":"OUT_OF_STOCK"}` → `201`
2. `POST /transactions` `{"userId":<id>,"productId":<that id>,"quantity":1}`
3. `POST /products` `{"name":"Empty","price":9,"quantity":0}`

**Expected:** step 2 → `409` (product not for sale). Status should be derived from, or validated against, quantity: `FOR_SALE` with 0 units, or `OUT_OF_STOCK` with 50 units, should be impossible, and restocking via `PUT` should flip status back to `FOR_SALE`.
**Actual:** step 2 → `201` (an item the merchant withdrew is sold). Step 3 → `201` with `status: "FOR_SALE"` and `quantity: 0`.

**Root cause:** `TransactionsService.create()` checks only `quantity`, never `status`. `status` is a free client-supplied field, and nothing re-computes it on create/update.
**Suggested fix:** reject purchases unless `status === FOR_SALE`. Derive status from quantity on every write (or drop the column and compute it).
**Tests:** `features/transaction/transaction.feature` › *Selling the last unit marks the product OUT_OF_STOCK* (`@known-bug @ignore`); `transactions.service.spec.ts` › `BUG-10`.

---

<a id="bug-03"></a>
## BUG-03 — `PUT` endpoints reject partial updates

| Field | Value |
| --- | --- |
| Severity | Medium |
| Endpoints | `PUT /products/:id`, `PUT /users/:id` |

**Steps:** `PUT /products/:id` `{"price":5}`; `PUT /users/:id` `{"name":"Renamed"}`
**Expected:** `200`, only the sent fields change. The services are written for this (`dto.name ?? product.name`, `preload`), and `UpdateProductDto`/`UpdateUserDto` (`PartialType`) exist for exactly this purpose.
**Actual:** `400` `["name must be a string","quantity must be a number…"]` / `["Email is required"]`.
**Root cause:** controllers bind `@Body() body: CreateProductDto` / `CreateUserDto`; the `Update*Dto` classes are dead code.
**Impact:** a client must resend every field to change one, which is what turns BUG-02 into "every update corrupts the price".
**Fix:** use `UpdateProductDto` / `UpdateUserDto` in controllers and services (or switch to `PATCH`).

---

<a id="bug-05"></a>
## BUG-05 — Email format is not validated

| Field | Value |
| --- | --- |
| Severity | Medium |
| Endpoint | `POST /users`, `PUT /users/:id` |

**Steps:** `POST /users` `{"name":"X","email":"not-an-email"}`, then `{"name":"Y","email":123456789}`
**Expected:** `400` "email must be an email".
**Actual:** both `201`. The numeric one is echoed back with `"email": 100264540`, a JSON number, which breaks the response contract.

Related: uniqueness is **case-sensitive**. `Case.Test@Example.com` and `case.test@example.com` both return `201` as two different users (verified), so one person can register many accounts.
**Root cause:** `CreateUserDto.email` only has `@IsNotEmpty()`. No `@IsEmail()` and no `@IsString()`. The unique index is on the raw value.
**Fix:** `@IsEmail()` + normalise with `@Transform(({value}) => value.trim().toLowerCase())` (or a `citext` column / unique index on `lower(email)`).

---

<a id="bug-06"></a>
## BUG-06 — Negative prices, empty product names and blank user names are accepted

| Field | Value |
| --- | --- |
| Severity | Medium |
| Endpoints | `POST/PUT /products`, `POST/PUT /users` |

| Request | Expected | Actual |
| --- | --- | --- |
| `POST /products {"price":-100,…}` | 400 | **201**, and the product can then be bought |
| `POST /products {"name":"",…}` | 400 | **201** |
| `POST /users {"name":"   ",…}` | 400 | **201** |

**Root cause:** `price` has only `@IsNumber()` (no `@Min(0)`/`@IsPositive()`). `name` has only `@IsString()` (no `@IsNotEmpty()`/`@Length(1, …)`). `@IsNotEmpty` on user name accepts whitespace. No precision limit on price (`numeric` with no scale), so `0.000001` is accepted.
**Fix:** `@IsPositive()` + `@IsNumber({ maxDecimalPlaces: 2 })` for price, `@IsNotEmpty()` + trim for names, `numeric(12,2)` column + `CHECK (price >= 0)`.

---

<a id="bug-07"></a>
## BUG-07 — Fractional or out-of-range quantity returns `500` instead of `400`

| Field | Value |
| --- | --- |
| Severity | Medium |
| Endpoint | `POST/PUT /products` |

**Steps / Actual:**
```json
POST /products {"name":"Frac","price":1,"quantity":1.5}
HTTP 500 {"statusCode":500,"message":"Error creating product","error":"invalid input syntax for type integer: \"1.5\""}

POST /products {"name":"Big","price":1,"quantity":3000000000}
HTTP 500 {"statusCode":500,"message":"Error creating product","error":"value \"3000000000\" is out of range for type integer"}
```
**Expected:** `400` with a validation message. Client input errors must never produce a 5xx: they page on-call and pollute error-rate SLOs.
**Root cause:** DTO uses `@IsNumber()` while the column is `integer`. Validation and schema disagree, so the DB is the first to reject. (`CreateTransactionDto` correctly uses `@IsInt()`.)
**Fix:** `@IsInt() @Min(0) @Max(2147483647)`.

---

<a id="bug-08"></a>
## BUG-08 — Malformed path ids return `500` instead of `400`

| Field | Value |
| --- | --- |
| Severity | Medium |
| Endpoints | `GET /users/:id`, `GET /products/:id`, `GET /transactions/:id`, `PUT …/:id` |

| Request | Expected | Actual |
| --- | --- | --- |
| `GET /products/abc` | 400 | **500** `invalid input syntax for type integer: "NaN"` |
| `GET /users/99999999999` | 400/404 | **500** `value "99999999999" is out of range for type integer` |
| `GET /transactions/1;DROP` | 400 | **500** |

**Root cause:** controllers do `Number(id)` on an unvalidated string, so `NaN` and >INT32 values go straight to PostgreSQL. (Parameterised queries mean this is not SQL injection, but it is an unhandled error path.)
**Fix:** `@Param('id', ParseIntPipe)` (or a custom pipe enforcing `1 ≤ id ≤ 2^31-1`).

---

<a id="bug-11"></a>
## BUG-11 — Raw database error messages are leaked to API clients

| Field | Value |
| --- | --- |
| Severity | Medium (security, OWASP API8 "Security Misconfiguration") |
| Endpoints | every `catch` block in all three services |

**Evidence:** see BUG-07/08. Before migrations existed the API also returned `relation "product" does not exist`, exposing table names. Transaction DB errors are returned as `409 "Database error: <driver message>"`.
**Expected:** generic message + correlation id to the client; full details only in server logs.
**Root cause:** `error: (error as Error).message` is copied into every `HttpException` body.
**Fix:** a global exception filter that logs the error with a request id and returns `{statusCode, message:"Internal server error", requestId}`. Remove per-method `catch` blocks.

---

<a id="bug-13"></a>
## BUG-13 — Read endpoints are unbounded and embed every related transaction (saturates the API under load)

| Field | Value |
| --- | --- |
| Severity | **High** (performance / availability: root cause of the load-test collapse) |
| Endpoints | `GET /products`, `GET /products/:id`, `GET /users`, `GET /users/:id`, `GET /transactions` |

**Evidence**
- `GET /products` grew from **264 B → 684 KB** during the test runs. `?limit=1&page=1` is ignored, and the full catalogue is returned.
- `GET /products/:id` for a product with 6,649 sales: **~200 ms, 684 KB**. The same call for a fresh product: **10 ms, 238 B** (3 samples each, `curl -w`).
- Load test (measured with the earlier single-file k6 script; analysis in `performance/README.md` § Results and analysis): checkout **alone** sustains 50 req/s at p95 13 ms. Add 27 req/s of product-detail reads on the same busy product and checkout p95 rises to **51 s**, with 11,740 arrivals dropped. Each detail read hydrates every transaction row into entities and serialises it on Node's single thread, so the event loop saturates and every other request queues.

**Root cause:** `find`/`findOne({ relations: ['transactions'] })` with no `take/skip`. Payload and CPU grow linearly with sales history, forever. A popular product becomes the slowest page in the shop, and reading it slows checkout for everyone. The FK columns `transaction.productId/userId` are also not indexed.
**Fix:** pagination (`?page&limit`, max 100), drop `transactions` from list views (expose `GET /products/:id/transactions` paginated), and index `transaction.productId` / `transaction.userId` (FK columns are not indexed in the migration).

---

<a id="bug-12"></a>
## BUG-12 — `price` type differs between create and read

| Field | Value |
| --- | --- |
| Severity | Low (contract) |
| Endpoint | `GET /products`, `GET /products/:id`, embedded `product` in transactions |

**Actual:** `POST` echoes `"price": 12.5` (number); every read returns `"price": "12.5"` (string).
**Root cause:** `@Column('decimal')`. The `pg` driver returns `numeric` as string to avoid float precision loss.
**Fix:** a column `transformer` (or integer minor units, i.e. cents, the usual choice for money) and a documented Swagger type.

---

## Other observations (not filed as bugs)

| Observation | Note |
| --- | --- |
| Unknown body fields are silently stripped | `whitelist: true` without `forbidNonWhitelisted`: typos like `{"prise": 5}` succeed silently. Recommend `forbidNonWhitelisted: true`. |
| No authentication/authorisation | Anyone can create products, change prices, and read all users' emails and purchase history (`GET /users`). Expected for an assessment app; a blocker for production. |
| Over-stock request returns `409` | Arguably `422`. Acceptable if documented. |
| `transaction.userId/productId` nullable, no `ON DELETE` rule | Schema allows orphaned transactions. |
| Swagger has no response schemas or examples | The `ApiResponse<T>` envelope is invisible in `/api`. |
| Response envelope repeats `statusCode` | Redundant with the HTTP status; harmless. |
