# syntax=docker/dockerfile:1

# ---- build: compile the Angular app to static files ----
FROM node:22-alpine AS build
WORKDIR /app
RUN corepack enable
# Dependencies first — this layer is reused until package.json / the lockfile change.
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
RUN --mount=type=cache,id=pnpm,target=/root/.local/share/pnpm/store pnpm install --frozen-lockfile
COPY . .
RUN pnpm build

# ---- runtime: nginx serves the files and proxies /api to the backend ----
FROM nginx:1.30-alpine
# Base images lag behind Alpine's security fixes; pull them in at build time (Trivy gate in CI).
RUN apk upgrade --no-cache
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html
EXPOSE 80
HEALTHCHECK --interval=10s --timeout=3s --retries=5 CMD wget -qO- http://127.0.0.1/healthz || exit 1
