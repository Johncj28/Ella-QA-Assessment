/**
 * User API - Stress: step 250 -> 400 -> 600/s until it breaks; aborts once errors > 20%.
 * Flow: POST /users -> GET /users/:id -> PUT /users/:id (-> GET /users every 10th iteration)
 *
 *   k6 run tests/user/stress.js
 */
import { stressOptions } from '../../src/config/loadprofiles.js';
import { healthCheck } from '../../src/utils/setup.js';
import { userFlow } from '../../src/scenarios/userFlow.js';
import { report } from '../../src/utils/report.js';

export const options = stressOptions('user_stress_test');

export function setup() {
  healthCheck();
}

export default function () {
  userFlow();
}

// HTML + JSON report in reports/ (see src/utils/report.js)
export const handleSummary = report('user', 'stress');
