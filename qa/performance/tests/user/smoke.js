/**
 * User API - Smoke: 5 VUs for 30s. Does the script work and is the API up?
 * Flow: POST /users -> GET /users/:id -> PUT /users/:id (-> GET /users every 10th iteration)
 *
 *   k6 run tests/user/smoke.js
 */
import { smokeOptions } from '../../src/config/loadprofiles.js';
import { healthCheck } from '../../src/utils/setup.js';
import { userFlow } from '../../src/scenarios/userFlow.js';
import { report } from '../../src/utils/report.js';

export const options = smokeOptions('user_smoke_test');


export default function () {
  userFlow();
}

// HTML + JSON report in reports/ (see src/utils/report.js)
export const handleSummary = report('user', 'smoke');
