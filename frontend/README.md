# Masternova — frontend

Angular 22 · standalone components · signals · Angular Material 3 · Vitest · angular-eslint.

```bash
pnpm start          # http://localhost:4200, /api proxied to :8080 (proxy.conf.json)
pnpm test           # Vitest, single run (pnpm test:watch to watch)
pnpm lint
pnpm format         # Prettier
pnpm build          # dist/frontend/browser, served by nginx in the Docker image
```

Structure and conventions: [`../CLAUDE.md`](../CLAUDE.md) §4.
