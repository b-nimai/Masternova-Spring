// k6: the SLO load test (D5.1). A realistic mix of the api's traffic at a fixed ARRIVAL rate, held
// against the SLOs of D3.5: availability 99.5 % (no 5xx) and latency p95 < 300 ms.
//
//   open model: `constant-arrival-rate` starts RATE requests per second whether or not the last ones
//   came back, so a slow api shows up as latency and dropped iterations — never as a politely lower
//   request rate (the "coordinated omission" a closed loop of VUs hides).
//
//   mix (weights per 100 requests, read-heavy like a catalog site):
//     35 list · 15 filter · 15 course detail · 5 reviews · 10 search · 5 typeahead   (anonymous)
//      5 my learning · 5 course progress · 5 progress heartbeat (a write)              (learners)
//
//   make k8s-seed COURSES=10000                     # the measurement dataset (+ the 50 learners)
//   RATE=150 k6 run load/slo-mix.js                 # one step, 3 min; thresholds = the SLOs
//   load/capacity-sweep.sh 50 100 200 300 400       # the steps, one table (docs/hld/03-capacity.md)
import http from 'k6/http';
import { check, fail } from 'k6';
import { BASE, insecureSkipTLSVerify, loginRequest } from './lib.js';

const RATE = Number(__ENV.RATE || 100);
const LEARNERS = Number(__ENV.LEARNERS || 50);
// the request kinds of the mix (each request is tagged with its name)
const NAMES = ['list', 'filter', 'course', 'reviews', 'search', 'suggest', 'learning', 'progress', 'heartbeat'];
const TERMS = ['kubernetes', 'spring boot', 'docker', 'postgres', 'react', 'system design', 'rust'];

export const options = {
  insecureSkipTLSVerify,
  scenarios: {
    mix: {
      executor: 'constant-arrival-rate',
      rate: RATE,
      timeUnit: '1s',
      duration: __ENV.DURATION || '3m',
      preAllocatedVUs: Math.max(20, RATE), // enough for ~1 s of in-flight requests…
      maxVUs: RATE * 4, // …and room to queue before k6 itself drops iterations
    },
  },
  // ⭐ the SLOs as pass/fail: availability 99.5 % and p95 < 300 ms for every request kind. Scoped to
  //    the scenario: setup()'s 50 logins + enrollments are not traffic and must not count.
  thresholds: {
    'http_req_failed{scenario:mix}': ['rate<0.005'],
    'http_req_duration{scenario:mix}': ['p(95)<300'],
    'http_reqs{scenario:mix}': ['count>0'], // listed so the summary export carries the step's own count
    // …and for every request kind of the mix: a kind added to NAMES is checked without a new line
    ...Object.fromEntries(NAMES.map((n) => [`http_req_duration{name:${n}}`, ['p(95)<300']])),
    dropped_iterations: ['count==0'], // k6 kept up: the rate asked for is the rate measured
  },
  summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
};

const json = { 'Content-Type': 'application/json' };

// Each learner logs in once, enrolls in a free course (PUT: idempotent, safe to repeat per run) and
// keeps its lecture ids. Access tokens live 15 min: a step is far shorter.
export function setup() {
  const page = http.get(`${BASE}/api/v1/courses?limit=50`).json();
  const tree = http.get(`${BASE}/api/v1/categories`).json();
  const flat = (nodes) => nodes.flatMap((n) => [n.slug, ...flat(n.children || [])]);

  const free = http.get(`${BASE}/api/v1/courses?price=FREE&limit=20`).json().items;
  if (!free.length) fail('no free course: make k8s-seed first');
  // the setup's ~120 requests in three batches, not one by one (logins are bcrypt-bound)
  const details = http.batch(free.map((c) => ['GET', `${BASE}/api/v1/courses/${c.slug}`]));
  const courses = details.map((r) => {
    const detail = r.json();
    return { id: detail.id, lectures: detail.sections.flatMap((s) => s.lectures.map((l) => l.id)) };
  });

  const emails = Array.from({ length: LEARNERS }, (_, i) => (i === 0 ? 'learner@seed.test' : `learner${i + 1}@seed.test`));
  const logins = http.batch(emails.map(loginRequest));
  logins.forEach((r, i) => {
    if (r.status !== 200) fail(`${emails[i]} login: ${r.status} (make k8s-seed first)`);
  });
  const learners = logins.map((r, i) => ({ token: r.json('accessToken'), course: courses[(i + 1) % courses.length] }));
  const enrolled = http.batch(
    learners.map((l) => ['PUT', `${BASE}/api/v1/me/enrollments/${l.course.id}`, null, { headers: { Authorization: `Bearer ${l.token}` } }]),
  );
  enrolled.forEach((r, i) => {
    if (r.status !== 200) fail(`${emails[i]} enroll: ${r.status} ${r.body}`);
  });
  return { courses: page.items.map((c) => ({ id: c.id, slug: c.slug })), categories: flat(tree), learners };
}

const pick = (xs) => xs[Math.floor(Math.random() * xs.length)];

function get(url, name, params = {}) {
  const res = http.get(url, { ...params, tags: { name } });
  check(res, { [`${name} 200`]: (r) => r.status === 200 });
  return res;
}

export default function ({ courses, categories, learners }) {
  const roll = Math.random() * 100;
  if (roll < 35) return get(`${BASE}/api/v1/courses?limit=20`, 'list');
  if (roll < 50) return get(`${BASE}/api/v1/courses?category=${pick(categories)}&sort=HIGHEST_RATED&limit=20`, 'filter');
  if (roll < 65) return get(`${BASE}/api/v1/courses/${pick(courses).slug}`, 'course');
  if (roll < 70) return get(`${BASE}/api/v1/courses/${pick(courses).id}/reviews?limit=10`, 'reviews');
  if (roll < 80) return get(`${BASE}/api/v1/search/courses?q=${encodeURIComponent(pick(TERMS))}`, 'search');
  if (roll < 85) return get(`${BASE}/api/v1/search/suggestions?q=${pick(TERMS).slice(0, 3)}`, 'suggest');

  const me = pick(learners);
  const auth = { headers: { Authorization: `Bearer ${me.token}` } };
  if (roll < 90) return get(`${BASE}/api/v1/me/learning`, 'learning', auth);
  if (roll < 95) return get(`${BASE}/api/v1/me/courses/${me.course.id}/progress`, 'progress', auth);
  const res = http.put(
    `${BASE}/api/v1/me/progress/${pick(me.course.lectures)}`,
    JSON.stringify({ positionSeconds: Math.floor(Math.random() * 400) }),
    { headers: { ...auth.headers, ...json }, tags: { name: 'heartbeat' } },
  );
  check(res, { 'heartbeat 200': (r) => r.status === 200 });
  return res;
}
