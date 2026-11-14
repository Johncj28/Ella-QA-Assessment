/**
 * Transaction API - Smoke: 5 VUs for 30s. Does the script work and is the API up?
 * Flow: GET /products/:id -> POST /transactions -> GET /transactions/:id
 *       (-> GET /transactions every 20th iteration)
 * teardown() verifies every unit sold was deducted from stock.
 *
 *   k6 run tests/transaction/smoke.js
 */
import { smokeOptions } from '../../src/config/loadprofiles.js';
import { checkStockIntegrity, healthCheck, seedProducts, seedUsers } from '../../src/utils/setup.js';
import { transactionFlow } from '../../src/scenarios/transactionFlow.js';
import { report } from '../../src/utils/report.js';

export const options = smokeOptions('transaction_smoke_test');

export function setup() {
  healthCheck();
  return { userIds: seedUsers(), productIds: seedProducts() };
}

export default function (data) {
  transactionFlow(data);
}

export function teardown(data) {
  checkStockIntegrity(data.productIds);
}

// HTML + JSON report in reports/ (see src/utils/report.js)
export const handleSummary = report('transaction', 'smoke');
