import http from 'k6/http';

const COUPON_ID = __ENV.COUPON_ID || '1';

export const options = {
    scenarios: { issue: {
            executor: 'constant-arrival-rate',
            rate: 5000, timeUnit: '1s', duration: '30s',
            preAllocatedVUs: 2000, maxVUs: 5000,
        } },
};

export default function () {
    const userId = (__VU * 1000000) + __ITER;
    http.post(`http://localhost:8080/api/coupons/${COUPON_ID}/issue`, null, {
        headers: { 'X-User-Id': String(userId) },
    });
}