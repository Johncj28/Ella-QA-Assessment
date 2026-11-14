import { group, sleep } from 'k6';
import { createProduct, getProduct, listProducts, updateProduct } from '../api/productApi.js';
import { expectStatus, idOf } from '../utils/checks.js';
import { everyNth, pick, thinkTime } from '../utils/data.js';
import { productPayload } from '../data/payload.js';

const API = 'products';
// GET /products returns the whole catalogue with every product's sales history
// embedded, so its cost grows with every transaction. Real shoppers mostly open
// product pages, so the full list is only fetched every Nth iteration (0 = never).
const LIST_EVERY = Number(__ENV.PRODUCT_LIST_EVERY ?? 10);

/** Catalogue management: create -> view -> edit, plus browsing existing products. */
export function productFlow(data) {
  group('Product catalogue', () => {
    const product = productPayload();
    const created = createProduct(product);
    if (!expectStatus(created, 201, 'create product', API)) return;
    const id = idOf(created);
    sleep(thinkTime());

    expectStatus(getProduct(id), 200, 'get product', API);
    sleep(thinkTime());

    expectStatus(updateProduct(id, { ...product, price: product.price + 1 }), 200, 'update product', API);
    sleep(thinkTime());

    if (data && data.productIds && data.productIds.length) {
      expectStatus(getProduct(pick(data.productIds)), 200, 'browse product', API);
    }

    if (everyNth(LIST_EVERY)) {
      sleep(thinkTime());
      expectStatus(listProducts(), 200, 'list products', API);
    }
  });
}
