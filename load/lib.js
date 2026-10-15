// Shared by the k6 scripts: where to send requests, and how to trust that host.
export const BASE = __ENV.BASE_URL || 'https://masternova.localhost:8443';

// The cluster's certificate comes from its own local CA (deploy/k3d/local-ca.yaml), which k6
// can't be pointed at: skip verification for *.localhost only, never for a real host.
export const insecureSkipTLSVerify = BASE.includes('.localhost');
