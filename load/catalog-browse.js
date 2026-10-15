// k6: anonymous visitors browsing the catalog — list, filter by category, open a course.
// The read path every visitor hits; used to drive the api's HPA (D4.6) and as the steady
// background load of the zero-downtime drill (D4.8).
//
//   k6 run load/catalog-browse.js                                     # the k8s stack, defaults
//   BASE_URL=http://localhost:8081 k6 run load/catalog-browse.js     # compose
//   VUS=40 HOLD=5m k6 run load/catalog-browse.js
import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE = __ENV.BASE_URL || 'https://masternova.localhost:8443';

export const options = {
  // the cluster's certificate comes from its own local CA (deploy/k3d/local-ca.yaml)
  insecureSkipTLSVerify: BASE.includes('.localhost'),
  stages: [
    { duration: '30s', target: Number(__ENV.VUS || 30) },
    { duration: __ENV.HOLD || '3m', target: Number(__ENV.VUS || 30) },
    { duration: '15s', target: 0 },
  ],
  thresholds: {
    // ⭐ zero failed requests is the point of the D4.8 drill: a rollout must not drop one
    http_req_failed: ['rate==0'],
    'http_req_duration{name:list}': ['p(95)<300'], // the latency SLO's edge (D3.5)
  },
};

export function setup() {
  const page = http.get(`${BASE}/api/v1/courses?limit=50`).json();
  const tree = http.get(`${BASE}/api/v1/categories`).json(); // roots with their children
  const flat = (nodes) => nodes.flatMap((n) => [n.slug, ...flat(n.children || [])]);
  return { slugs: page.items.map((c) => c.slug), categories: flat(tree) };
}

export default function ({ slugs, categories }) {
  const list = http.get(`${BASE}/api/v1/courses?limit=20`, { tags: { name: 'list' } });
  check(list, { 'list 200': (r) => r.status === 200 });

  if (categories.length) {
    const cat = categories[Math.floor(Math.random() * categories.length)];
    http.get(`${BASE}/api/v1/courses?category=${cat}&limit=20`, { tags: { name: 'filter' } });
  }
  if (slugs.length) {
    const slug = slugs[Math.floor(Math.random() * slugs.length)];
    const course = http.get(`${BASE}/api/v1/courses/${slug}`, { tags: { name: 'course' } });
    check(course, { 'course 200': (r) => r.status === 200 });
  }
  sleep(0.5);
}
