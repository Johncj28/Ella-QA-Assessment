/**
 * User API - Soak: 125/s held for 2h (-e SOAK_DURATION=...): leaks and slow degradation.
 * Flow: POST /users -> GET /users/:id -> PUT /users/:id (-> GET /users every 10th iteration)
 *
 *   k6 run tests/user/soak.js
 */
import { soakOptions } from '../../src/config/loadprofiles.js';
import { healthCheck } from '../../src/utils/setup.js';
import { userFlow } from '../../src/scenarios/userFlow.js';
import { report } from '../../src/utils/report.js';

export const options = soakOptions('user_soak_test');

export function setup() {
  healthCheck();
}

export default function () {
  userFlow();
}

// HTML + JSON report in reports/ (see src/utils/report.js)
export const handleSummary = report('user', 'soak');
