
const ENVIRONMENTS = {
    local: 'http://localhost:4000',
    // staging: 'http://<staging-host>:4000',
};

export const selectedENV = (__ENV.ENV || 'local').toLowerCase();
export const CONFIG ={
    ENV: selectedENV,
    BASE_API_URL: (__ENV.BASE_URL || ENVIRONMENTS[selectedENV] || ENVIRONMENTS.local),
    LOCAL_URL: ENVIRONMENTS.local,
    THINK_TIME_SECONDS: Number(__ENV.THINK_TIME_SECONDS || 2),
    THINK_TIME_MIN: Number(__ENV.THINK_TIME_MIN || 1),
    THINK_TIME_MAX: Number(__ENV.THINK_TIME_MAX || 5),
}