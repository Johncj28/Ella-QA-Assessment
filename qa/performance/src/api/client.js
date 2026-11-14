import http from 'k6/http';
import { CONFIG } from '../config/enviroment.js';

const params = (tags) => ({
  headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
  timeout: __ENV.HTTP_TIMEOUT || '30s',
  // `name` groups URLs like /users/42 and /users/43 into one series (GET /users/:id)
  // so Grafana and the summary show one line per endpoint, not one per id.
  tags,
});

const url = (path) => `${CONFIG.BASE_API_URL}${path}`;

export const get = (path, tags) => http.get(url(path), params(tags));
export const post = (path, body, tags) => http.post(url(path), JSON.stringify(body), params(tags));
export const put = (path, body, tags) => http.put(url(path), JSON.stringify(body), params(tags));
