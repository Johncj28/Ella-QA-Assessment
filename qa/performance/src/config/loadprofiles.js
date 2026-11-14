import { THRESHOLDS } from "./threshold.js";
import { CONFIG } from "./enviroment.js";
import { endpointThresholds } from "../utils/report.js";

/**
 * Shared k6 options factories.
 * Values match the existing smoke/load profiles used across ecommerce tests.
 *
 * Optional knobs:
 *   -e RATE_SCALE=0.2      multiply every arrival rate (weaker environment / quick check)
 *   -e SOAK_DURATION=4h    length of the soak hold
 *   -e TEST_ID=...         run name shown in Grafana (default: <name>-<timestamp>)
 */
const RATE_SCALE = Number(__ENV.RATE_SCALE || 1);
const rate = (n) => (n === 0 ? 0 : Math.max(1, Math.round(n * RATE_SCALE)));
const scaleStages = (stages) => stages.map((s) => ({ ...s, target: rate(s.target) }));

/** Adds per-endpoint tracking so the HTML report can show the "Endpoints" tab (user_smoke_test -> user). */
const withEndpoints = (thresholds, scenarioName) => ({ ...thresholds, ...endpointThresholds(scenarioName.split("_")[0]) });

/** Tags attached to every metric, so Grafana can filter by run, test type and environment. */
function runOptions(scenarioName, type) {
    return {
        tags: {
            testid: __ENV.TEST_ID || `${scenarioName}-${new Date().toISOString().slice(0, 16)}`,
            test_type: type,
            env: CONFIG.ENV,
        },
        summaryTrendStats: ["avg", "min", "med", "p(90)", "p(95)", "p(99)", "max"],
        setupTimeout: "3m",
    };
}

export function smokeOptions(scenarioName = "smoke_test") {
    return {
        ...runOptions(scenarioName, "smoke"),
        scenarios: {
            [scenarioName]: {
                executor: "constant-vus",
                vus: 5,
                duration: "30s",
            },
        },
        thresholds: withEndpoints(THRESHOLDS.smoke, scenarioName),
    };
}

export function loadOptions(scenarioName = "user_load_test") {
    return {
        ...runOptions(scenarioName, "load"),
        scenarios: {
            [scenarioName]: {
                executor: "ramping-arrival-rate",
                startRate: rate(20),
                timeUnit: "1s",
                preAllocatedVUs: 150,
                maxVUs: 1000,
                stages: scaleStages([
                    { duration: "2m", target: 125 }, // ramp to ~50% of peak (normal load)
                    { duration: "5m", target: 125 }, // hold — baseline soak
                    { duration: "2m", target: 250 }, // ramp to peak (250 RPS target)
                    { duration: "10m", target: 250 }, // hold — sustained peak load, the real test window
                    { duration: "3m", target: 0 }, // ramp down — observe recovery
                ]),
            },
        },
        thresholds: withEndpoints(THRESHOLDS.load, scenarioName),
    };
}

/** Step above the 250/s peak until the API breaks; aborts automatically once errors exceed 20%. */
export function stressOptions(scenarioName = "stress_test") {
    return {
        ...runOptions(scenarioName, "stress"),
        scenarios: {
            [scenarioName]: {
                executor: "ramping-arrival-rate",
                startRate: rate(50),
                timeUnit: "1s",
                preAllocatedVUs: 300,
                maxVUs: 2000,
                stages: scaleStages([
                    { duration: "2m", target: 250 }, // up to normal peak
                    { duration: "3m", target: 250 },
                    { duration: "2m", target: 400 }, // 1.6x peak
                    { duration: "3m", target: 400 },
                    { duration: "2m", target: 600 }, // 2.4x peak: expected breaking zone
                    { duration: "3m", target: 600 },
                    { duration: "3m", target: 0 }, // recovery
                ]),
            },
        },
        thresholds: withEndpoints(THRESHOLDS.stress, scenarioName),
    };
}

/** Normal load (125/s) held for a long time: memory leaks, pool exhaustion, slow degradation. */
export function soakOptions(scenarioName = "soak_test") {
    return {
        ...runOptions(scenarioName, "soak"),
        scenarios: {
            [scenarioName]: {
                executor: "ramping-arrival-rate",
                startRate: rate(10),
                timeUnit: "1s",
                preAllocatedVUs: 150,
                maxVUs: 1000,
                stages: scaleStages([
                    { duration: "5m", target: 125 }, // ramp up
                    { duration: __ENV.SOAK_DURATION || "2h", target: 125 }, // long hold
                    { duration: "5m", target: 0 }, // ramp down
                ]),
            },
        },
        thresholds: withEndpoints(THRESHOLDS.soak, scenarioName),
    };
}

// ///limits:
//           cpu: 500m
//           memory: 512Mi
//         requests:
//           cpu: 250m
//           memory: 256Mi
