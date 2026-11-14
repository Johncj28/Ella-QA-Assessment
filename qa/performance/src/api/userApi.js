import { get, post, put } from './client.js';
import { ENDPOINTS } from '../config/endpoints.js';

const { USERS } = ENDPOINTS;
const tag = (name, api = 'users') => ({ api, name: api === 'setup' ? `setup ${name}` : name });

export const createUser = (user, api) => post(USERS, user, tag(`POST ${USERS}`, api));
export const listUsers = () => get(USERS, tag(`GET ${USERS}`));
export const getUser = (id) => get(`${USERS}/${id}`, tag(`GET ${USERS}/:id`));
export const updateUser = (id, user) => put(`${USERS}/${id}`, user, tag(`PUT ${USERS}/:id`));
