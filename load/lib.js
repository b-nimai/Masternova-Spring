// Shared by the k6 scripts: where to send requests, and how to trust that host.
export const BASE = __ENV.BASE_URL || 'https://masternova.localhost:8443';

// The cluster's certificate comes from its own local CA (deploy/k3d/local-ca.yaml), which k6
// can't be pointed at: skip verification for *.localhost only, never for a real host.
export const insecureSkipTLSVerify = BASE.includes('.localhost');

// Seeded accounts (docs/db/seed-catalog.sql) share one password. A login as an http.batch entry,
// so many can be sent at once (slo-mix's 50 learners), and as one call.
export const SEED_PASSWORD = 'seed-password';
export function loginRequest(email) {
  return {
    method: 'POST',
    url: `${BASE}/api/v1/auth/login`,
    body: JSON.stringify({ email, password: SEED_PASSWORD }),
    params: { headers: { 'Content-Type': 'application/json' }, tags: { name: 'login' } },
  };
}
