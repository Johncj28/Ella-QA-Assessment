/**
 * Product API - Soak: 125/s held for 2h (-e SOAK_DURATION=...): leaks and slow degradation.
 * Flow: POST /products -> GET /products/:id -> PUT /products/:id -> browse a seeded product
 *       (-> GET /products every 10th iteration; -e PRODUCT_LIST_EVERY=0 to skip it)
 *
 *   k6 run tests/product/soak.js
 */
import { soakOptions } from '../../src/config/loadprofiles.js';
import { healthCheck, seedProducts } from '../../src/utils/setup.js';
import { productFlow } from '../../src/scenarios/productFlow.js';
import { report } from '../../src/utils/report.js';

export const options = soakOptions('product_soak_test');

export function setup() {
  healthCheck();
  return { productIds: seedProducts() };
}

export default function (data) {
  productFlow(data);
}

// HTML + JSON report in reports/ (see src/utils/report.js)
export const handleSummary = report('product', 'soak');
