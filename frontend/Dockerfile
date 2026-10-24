# syntax=docker/dockerfile:1

# ---- build: compile the Angular app to static files ----
FROM node:26-alpine AS build
WORKDIR /app
# Dependencies first — this layer is reused until package.json / the lockfile change.
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
# Install the exact pnpm pinned in package.json ("packageManager"). Not corepack: Node 25+ dropped it.
RUN npm install -g "$(node -p "require('./package.json').packageManager")"
RUN --mount=type=cache,id=pnpm,target=/root/.local/share/pnpm/store pnpm install --frozen-lockfile
COPY . .
RUN pnpm build

# ---- runtime: nginx serves the files and proxies /api to the backend ----
FROM nginx:1.31-alpine
# Base images lag behind Alpine's security fixes; pull them in at build time (Trivy gate in CI).
# APK_REFRESH (CI: today's date) re-runs this layer daily — a cached upgrade is a stale upgrade.
ARG APK_REFRESH=
RUN echo "packages as of ${APK_REFRESH:-build}" && apk upgrade --no-cache
COPY nginx.conf /etc/nginx/conf.d/default.conf
# Security headers (D5.5): rendered at start by the image's envsubst step into /var/run (writable
# in a read-only container). STORAGE_ORIGIN is where the browser uploads to and streams from; each
# environment sets it, so one image serves every one.
COPY security-headers.conf.template /etc/nginx/templates/security-headers.conf.template
ENV STORAGE_ORIGIN=http://localhost:9010 \
    NGINX_ENVSUBST_OUTPUT_DIR=/var/run \
    NGINX_ENVSUBST_FILTER=^STORAGE_ORIGIN$
COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html
EXPOSE 80
HEALTHCHECK --interval=10s --timeout=3s --retries=5 CMD wget -qO- http://127.0.0.1/healthz || exit 1
