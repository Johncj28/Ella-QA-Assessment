import { get, post, put } from './client.js';
import { ENDPOINTS } from '../config/endpoints.js';

const { PRODUCTS } = ENDPOINTS;
const tag = (name, api = 'products') => ({ api, name: api === 'setup' ? `setup ${name}` : name });

export const createProduct = (product, api) => post(PRODUCTS, product, tag(`POST ${PRODUCTS}`, api));
export const listProducts = () => get(PRODUCTS, tag(`GET ${PRODUCTS}`));
export const getProduct = (id, api) => get(`${PRODUCTS}/${id}`, tag(`GET ${PRODUCTS}/:id`, api));
export const updateProduct = (id, product) => put(`${PRODUCTS}/${id}`, product, tag(`PUT ${PRODUCTS}/:id`));
