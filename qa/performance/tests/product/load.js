/**
 * Product API - Load: ramp to 125/s, then 250/s peak held for 10 min (~22 min total).
 * Flow: POST /products -> GET /products/:id -> PUT /products/:id -> browse a seeded product
 *       (-> GET /products every 10th iteration; -e PRODUCT_LIST_EVERY=0 to skip it)
 *
 *   k6 run tests/product/load.js
 */
import { loadOptions } from '../../src/config/loadprofiles.js';
import { healthCheck, seedProducts } from '../../src/utils/setup.js';
import { productFlow } from '../../src/scenarios/productFlow.js';
import { report } from '../../src/utils/report.js';

export const options = loadOptions('product_load_test');

export function setup() {
  healthCheck();
  return { productIds: seedProducts() };
}

export default function (data) {
  productFlow(data);
}

// HTML + JSON report in reports/ (see src/utils/report.js)
export const handleSummary = report('product', 'load');
