/**
 * Transaction API - Load: ramp to 125/s, then 250/s peak held for 10 min (~22 min total).
 * Flow: GET /products/:id -> POST /transactions -> GET /transactions/:id
 *       (-> GET /transactions every 20th iteration)
 * teardown() verifies every unit sold was deducted from stock.
 *
 *   k6 run tests/transaction/load.js
 */
import { loadOptions } from '../../src/config/loadprofiles.js';
import { checkStockIntegrity, healthCheck, seedProducts, seedUsers } from '../../src/utils/setup.js';
import { transactionFlow } from '../../src/scenarios/transactionFlow.js';
import { report } from '../../src/utils/report.js';

export const options = loadOptions('transaction_load_test');

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
export const handleSummary = report('transaction', 'load');
