/**
 * User API - Load: ramp to 125/s, then 250/s peak held for 10 min (~22 min total).
 * Flow: POST /users -> GET /users/:id -> PUT /users/:id (-> GET /users every 10th iteration)
 *
 *   k6 run tests/user/load.js
 */
import { loadOptions } from '../../src/config/loadprofiles.js';
import { healthCheck } from '../../src/utils/setup.js';
import { userFlow } from '../../src/scenarios/userFlow.js';
import { report } from '../../src/utils/report.js';

export const options = loadOptions('user_load_test');

export function setup() {
  healthCheck();
}

export default function () {
  userFlow();
}

// HTML + JSON report in reports/ (see src/utils/report.js)
export const handleSummary = report('user', 'load');
