/**
 * Product API - Smoke: 5 VUs for 30s. Does the script work and is the API up?
 * Flow: POST /products -> GET /products/:id -> PUT /products/:id -> browse a seeded product
 *       (-> GET /products every 10th iteration; -e PRODUCT_LIST_EVERY=0 to skip it)
 *
 *   k6 run tests/product/smoke.js
 */
import { smokeOptions } from '../../src/config/loadprofiles.js';
import { healthCheck, seedProducts } from '../../src/utils/setup.js';
import { productFlow } from '../../src/scenarios/productFlow.js';
import { report } from '../../src/utils/report.js';

export const options = smokeOptions('product_smoke_test');

export function setup() {
  healthCheck();
  return { productIds: seedProducts() };
}

export default function (data) {
  productFlow(data);
}

// HTML + JSON report in reports/ (see src/utils/report.js)
export const handleSummary = report('product', 'smoke');
