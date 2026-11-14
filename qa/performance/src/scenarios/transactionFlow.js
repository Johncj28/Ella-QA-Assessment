import { group, sleep } from 'k6';
import { getProduct } from '../api/productApi.js';
import { createTransaction, getTransaction, listTransactions } from '../api/transactionApi.js';
import { expectStatus, idOf } from '../utils/checks.js';
import { everyNth, pick, thinkTime } from '../utils/data.js';
import { transactionPayload } from '../data/payload.js';

const API = 'transactions';
const LIST_EVERY = Number(__ENV.TXN_LIST_EVERY ?? 20);

/**
 * Checkout: view product -> buy -> view receipt. Users and products come from
 * setup(), so this measures purchasing only, not registration.
 */
export function transactionFlow(data) {
  group('Checkout', () => {
    const userId = pick(data.userIds);
    const productId = pick(data.productIds);

    // tagged api=transactions: in this flow the product page is part of checkout
    expectStatus(getProduct(productId, API), 200, 'view product before buying', API);
    sleep(thinkTime());

    const bought = createTransaction(transactionPayload(userId, productId));
    if (!expectStatus(bought, 201, 'create transaction', API)) return;
    sleep(thinkTime());

    expectStatus(getTransaction(idOf(bought)), 200, 'get transaction', API);

    if (everyNth(LIST_EVERY)) {
      sleep(thinkTime());
      expectStatus(listTransactions(), 200, 'list transactions', API);
    }
  });
}
