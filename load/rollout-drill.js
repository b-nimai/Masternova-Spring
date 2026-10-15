// k6: the zero-downtime drill (D4.8). Run it while something changes underneath — a rollout, a
// node drain, a migration — and it must end with ZERO failed requests and ZERO failed checks.
//
//   browse: anonymous catalog reads (CourseSummary reads course.subtitle)
//   author: the seed instructor creates a course and rewrites its details, then reads them back:
//           a write that one app version made must read back identically from any other version
//           (the round-trip is what catches a broken dual-write in an expand–contract migration)
//
//   k6 run load/rollout-drill.js                    # 5 min against the k8s stack
//   DURATION=8m k6 run load/rollout-drill.js
import http from 'k6/http';
import { check, fail, sleep } from 'k6';

const BASE = __ENV.BASE_URL || 'https://masternova.localhost:8443';
const DURATION = __ENV.DURATION || '5m';

export const options = {
  insecureSkipTLSVerify: BASE.includes('.localhost'), // the cluster's local CA
  scenarios: {
    browse: { executor: 'constant-vus', vus: 10, duration: DURATION, exec: 'browse' },
    author: { executor: 'constant-vus', vus: 2, duration: DURATION, exec: 'author' },
  },
  thresholds: {
    http_req_failed: ['rate==0'],
    checks: ['rate==1'],
  },
};

function logIn() {
  const login = http.post(
    `${BASE}/api/v1/auth/login`,
    JSON.stringify({ email: 'instructor@seed.test', password: 'seed-password' }),
    { headers: { 'Content-Type': 'application/json' }, tags: { name: 'login' } },
  );
  if (login.status !== 200) fail(`seed instructor login: ${login.status} (make k8s-seed first)`);
  return login.json('accessToken');
}

export function setup() {
  logIn(); // fail fast, before any load, if the seed instructor is missing
}

// Per VU: access tokens live 15 min and DURATION may be longer. Log in again after 10 min, so an
// expired token is never mistaken for the deployment failing.
let token = null;
let tokenAt = 0;
function currentToken() {
  if (token === null || Date.now() - tokenAt > 10 * 60 * 1000) {
    token = logIn();
    tokenAt = Date.now();
  }
  return token;
}

export function browse() {
  const list = http.get(`${BASE}/api/v1/courses?limit=20`, { tags: { name: 'list' } });
  check(list, { 'list 200': (r) => r.status === 200 });
  sleep(0.3);
}

export function author() {
  const headers = { Authorization: `Bearer ${currentToken()}`, 'Content-Type': 'application/json' };
  const created = http.post(
    `${BASE}/api/v1/instructor/courses`,
    JSON.stringify({ title: `Drill ${__VU}-${__ITER}`, categorySlug: 'web-development', level: 'BEGINNER', language: 'en' }),
    { headers: { ...headers, 'Idempotency-Key': crypto.randomUUID() }, tags: { name: 'create' } },
  );
  if (!check(created, { 'create 201': (r) => r.status === 201 })) return;
  const course = created.json();

  const subtitle = `written by VU ${__VU} at ${Date.now()}`;
  const updated = http.put(
    `${BASE}/api/v1/instructor/courses/${course.id}/details`,
    JSON.stringify({
      expectedVersion: course.version,
      title: course.title,
      subtitle,
      description: 'A course created by the rollout drill.',
      categorySlug: 'web-development',
      level: 'BEGINNER',
      language: 'en',
    }),
    { headers, tags: { name: 'details' } },
  );
  check(updated, { 'details 200': (r) => r.status === 200 });

  const read = http.get(`${BASE}/api/v1/instructor/courses/${course.id}`, { headers, tags: { name: 'read-back' } });
  check(read, { 'subtitle reads back': (r) => r.status === 200 && r.json('subtitle') === subtitle });
  sleep(1);
}
