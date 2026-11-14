import { SharedArray } from 'k6/data';
import { pick, randInt, uniqueId } from '../utils/data.js';

/**
 * Request bodies for the Ella API. Every user/product is unique per VU + iteration,
 * so concurrent VUs never collide (e.g. on email).
 */

// SharedArray keeps one read-only copy in memory instead of one per VU.
// decodeURI: import.meta.resolve() returns a URL, so a space in the path becomes %20, which open() can't find.
const productTemplates = new SharedArray('products', () => JSON.parse(open(decodeURI(import.meta.resolve('./products.json')))));

/** POST/PUT /users */
export function userPayload() {
  const id = uniqueId();
  return { name: `Perf User ${id}`, email: `perf.${id}@load.test` };
}

/** POST/PUT /products */
export function productPayload(quantity = randInt(50, 500)) {
  const t = pick(productTemplates);
  return { name: `${t.name} ${uniqueId()}`, price: t.price, quantity, status: 'FOR_SALE' };
}

/** POST /transactions */
export function transactionPayload(userId, productId, quantity = 1) {
  return { userId, productId, quantity };
}
