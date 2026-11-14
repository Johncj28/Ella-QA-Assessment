import { check } from 'k6';
import { Rate } from 'k6/metrics';

/**
 * Share of requests that did not do what the user wanted (wrong status or no
 * `data` in the body). Unlike http_req_failed it also catches a 200 with an
 * error payload. Tagged per API so thresholds and Grafana can split it.
 */
export const businessErrors = new Rate('business_errors');

const body = (res, path) => {
  try {
    return res.json(path);
  } catch (e) {
    return undefined; // timeout, empty or non-JSON body
  }
};

/** Check status + `data` envelope ({ statusCode, message, data }) and record the outcome. */
export function expectStatus(res, status, label, api) {
  const ok = check(
    res,
    {
      [`${label}: status ${status}`]: (r) => r.status === status,
      [`${label}: has data`]: (r) => body(r, 'data') !== undefined && body(r, 'data') !== null,
    },
    { api },
  );
  businessErrors.add(!ok, { api });
  return ok;
}

export const idOf = (res) => body(res, 'data.id');
