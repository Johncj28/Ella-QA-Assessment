import { get, post } from './client.js';
import { ENDPOINTS } from '../config/endpoints.js';

const { TRANSACTIONS } = ENDPOINTS;
const tag = (name) => ({ api: 'transactions', name });

export const createTransaction = (txn) => post(TRANSACTIONS, txn, tag(`POST ${TRANSACTIONS}`));
export const listTransactions = () => get(TRANSACTIONS, tag(`GET ${TRANSACTIONS}`));
export const getTransaction = (id) => get(`${TRANSACTIONS}/${id}`, tag(`GET ${TRANSACTIONS}/:id`));
