export const THRESHOLDS = {
    smoke: {
        'http_req_duration': ['p(95)<500'], // 95% of requests must complete below 500ms
        'http_req_failed': ['rate<0.01'], // http errors should be less than 1%
        checks: ['rate>0.99'], // checks should pass
    },
    load: {
        'http_req_duration': ['p(95)<1000'], // 95% of requests must complete below 1s
        'http_req_failed': ['rate<0.01'],
        checks: ['rate>0.99'], // checks should pass
    },
    stress: {
        // Stress looks for the breaking point, so the limits are looser...
        'http_req_duration': ['p(95)<2000'],
        'http_req_failed': [
            'rate<0.05',
            // ...and the run stops early once the API is clearly down (>20% errors for 30s)
            { threshold: 'rate<0.20', abortOnFail: true, delayAbortEval: '30s' },
        ],
        checks: ['rate>0.95'],
    },
    soak: {
        // Same limits as load: latency must not degrade over a long run
        'http_req_duration': ['p(95)<1000', 'p(99)<2000'],
        'http_req_failed': ['rate<0.01'],
        checks: ['rate>0.99'],
    },
}
