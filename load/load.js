import http from 'k6/http';
import { check } from 'k6';

// Funded account UUIDs seeded into account-db.
const accounts = JSON.parse(open('./accounts.json'));

export const options = {
  scenarios: {
    ramp: {
      executor: 'ramping-arrival-rate',
      startRate: 100,
      timeUnit: '1s',
      preAllocatedVUs: 200,
      maxVUs: 1000,
      stages: [
        { target: 300, duration: '10s' },
        { target: 600, duration: '10s' },
        { target: 1000, duration: '10s' },
        { target: 1200, duration: '15s' },
      ],
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.02'],
    http_req_duration: ['p(95)<500'],
  },
};

function pick() {
  return accounts[Math.floor(Math.random() * accounts.length)];
}

export default function () {
  const from = pick();
  let to = pick();
  while (to === from) to = pick();

  // Unique idempotency key per request so every call is a real transfer (no dedupe).
  const key = `lt-${__VU}-${__ITER}-${Date.now()}`;

  const res = http.post(
    'http://localhost:8080/api/transfers',
    JSON.stringify({ idempotencyKey: key, fromAccountId: from, toAccountId: to, amount: 1, currency: 'KGS' }),
    { headers: { 'Content-Type': 'application/json' } },
  );
  check(res, { 'created (201)': (r) => r.status === 201 });
}
