import exec from 'k6/execution';
import { CONFIG } from '../config/enviroment.js';

export const pick = (arr) => arr[Math.floor(Math.random() * arr.length)];
export const randInt = (min, max) => Math.floor(Math.random() * (max - min + 1)) + min;

/** Unique per VU + iteration + run. */
export function uniqueId() {
  let vu = 0;
  let iter = 0;
  try {
    vu = exec.vu.idInTest;
    iter = exec.vu.iterationInScenario;
  } catch (e) {
    // setup()/teardown() run outside a VU
  }
  return `${Date.now()}-${vu}-${iter}-${randInt(0, 9999)}`;
}

/**
 * Seconds to pause between user steps: random between THINK_TIME_MIN and THINK_TIME_MAX.
 * -e THINK_TIME_MAX=0 disables it.
 */
export function thinkTime() {
  const { THINK_TIME_MIN: min, THINK_TIME_MAX: max } = CONFIG;
  if (max <= 0) return 0;
  return min + Math.random() * Math.max(0, max - min);
}

/** True on every Nth iteration. Used for expensive calls that real users make rarely. */
export function everyNth(n) {
  if (!n || n <= 0) return false;
  try {
    return exec.scenario.iterationInTest % n === 0;
  } catch (e) {
    return false;
  }
}
