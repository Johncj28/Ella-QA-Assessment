import http from 'k6/http';
import { fail } from 'k6';
import { CONFIG } from '../config/enviroment.js';
import { ENDPOINTS } from '../config/endpoints.js';
import { createUser } from '../api/userApi.js';
import { createProduct, getProduct } from '../api/productApi.js';
import { idOf } from './checks.js';
import { productPayload, userPayload } from '../data/payload.js';

export const SEED_STOCK = 1000000;

/** Stop before generating load if the API is not up. */
export function healthCheck() {
  const res = http.get(`${CONFIG.BASE_API_URL}${ENDPOINTS.HEALTH}`, { tags: { api: 'setup', name: `GET ${ENDPOINTS.HEALTH}` } });
  if (res.status !== 200) fail(`API not healthy at ${CONFIG.BASE_API_URL}: HTTP ${res.status} ${res.body || res.error}`);
}

/** Create test users through the public API (tagged api=setup, so Grafana can hide them). */
export function seedUsers(count = Number(__ENV.SEED_USERS || 20)) {
  const ids = [];
  for (let i = 0; i < count; i++) {
    const res = createUser(userPayload(), 'setup');
    if (res.status !== 201) fail(`seed user failed: HTTP ${res.status} ${res.body}`);
    ids.push(idOf(res));
  }
  return ids;
}

/**
 * Create products with very high stock, so stock never runs out mid-test:
 * we want to measure the platform, not hit "out of stock".
 */
export function seedProducts(count = Number(__ENV.SEED_PRODUCTS || 5), stock = SEED_STOCK) {
  const ids = [];
  for (let i = 0; i < count; i++) {
    const res = createProduct(productPayload(stock), 'setup');
    if (res.status !== 201) fail(`seed product failed: HTTP ${res.status} ${res.body}`);
    ids.push(idOf(res));
  }
  return ids;
}

/**
 * After a transaction test: every unit sold must have been deducted from stock.
 * Catches lost updates under concurrency and stock moving the wrong way.
 */
export function checkStockIntegrity(productIds, initialStock = SEED_STOCK) {
  let sold = 0;
  let stockMoved = 0;
  for (const id of productIds) {
    const res = getProduct(id, 'setup');
    if (res.status !== 200) continue;
    const txns = res.json('data.transactions') || [];
    sold += txns.reduce((sum, t) => sum + (t.quantity || 0), 0);
    stockMoved += initialStock - res.json('data.quantity');
  }
  console.log(`[integrity] units sold (recorded transactions): ${sold}`);
  console.log(`[integrity] stock decrease observed            : ${stockMoved}`);
  if (stockMoved < 0) {
    console.warn(`[integrity] DEFECT: purchases INCREASED stock by ${-stockMoved} instead of reducing it`);
  } else if (sold !== stockMoved) {
    console.warn(`[integrity] DEFECT: ${sold - stockMoved} unit(s) sold but not deducted from stock (lost updates under concurrency)`);
  } else {
    console.log('[integrity] OK: every unit sold was deducted from stock');
  }
}
