# Day-to-day commands. `make help` lists them.
.DEFAULT_GOAL := help
MVN := cd backend && ./mvnw -B

help: ## List targets
	@grep -E '^[a-z0-9-]+:.*## ' $(MAKEFILE_LIST) | awk -F':.*## ' '{printf "  \033[36m%-14s\033[0m %s\n", $$1, $$2}'

up: ## Start infra (postgres, redis, mailpit, SeaweedFS S3 + its bucket/CORS init job)
	docker compose up -d --wait postgres redis mailpit seaweedfs media-init

down: ## Stop everything (volumes kept)
	docker compose --profile app --profile observability down

nuke: ## Stop everything AND delete volumes (fresh database)
	docker compose --profile app --profile observability down -v

secrets: ## Generate the local secret files the container stack mounts (gitignored, once per machine)
	@mkdir -p secrets
	@for f in jwt_access_secret notification_token_secret playback_token_secret entitlement_playback_secret progress_beacon_secret db_password db_reader_password redis_password; do \
	  [ -s secrets/$$f ] || { head -c 48 /dev/urandom | base64 | tr -d '\n' > secrets/$$f; \
	    chmod 644 secrets/$$f; echo "created secrets/$$f"; }; \
	done

stack: secrets ## Build images and run the whole app in containers (web on :8081)
	docker compose --profile app up -d --build --wait postgres redis mailpit api worker web

obs: ## Start observability: Grafana :3001, Prometheus :9091, Alertmanager :9094, Tempo, Loki + Alloy
	docker compose --profile observability up -d --wait prometheus alertmanager tempo loki alloy grafana

OBS := $(CURDIR)/deploy/observability
# keep in step with compose.yaml's prometheus image
PROM := prom/prometheus:v3.15.0
obs-check: ## Validate observability config and unit-test the alert rules (promtool, amtool) — also in CI
	docker run --rm --entrypoint promtool -v $(OBS)/prometheus:/etc/prometheus:ro -w /etc/prometheus \
	  $(PROM) check config prometheus.yml
	docker run --rm --entrypoint promtool -v $(OBS)/prometheus:/etc/prometheus:ro -w /etc/prometheus/tests \
	  $(PROM) test rules alerts.test.yml
	docker run --rm --entrypoint amtool -v $(OBS)/alertmanager:/etc/alertmanager:ro \
	  prom/alertmanager:v0.34.1 check-config /etc/alertmanager/alertmanager.yml
	@tmp=$$(mktemp -d) && chmod 755 $$tmp && python3 $(OBS)/scripts/dashboards_as_rules.py $(OBS)/grafana/dashboards $$tmp/dashboards.rules.json && chmod 644 $$tmp/dashboards.rules.json \
	  && docker run --rm --entrypoint promtool -v $$tmp:/q:ro $(PROM) check rules /q/dashboards.rules.json; \
	  rc=$$?; rm -rf $$tmp; exit $$rc

api: ## Run the api from source (:8080)
	$(MVN) -q install -DskipTests -Djacoco.skip -Dspotless.check.skip && PAYMENT_PROVIDER=$${PAYMENT_PROVIDER:-fake} ./mvnw -pl api spring-boot:run

worker: ## Run the worker from source (:8090)
	$(MVN) -q install -DskipTests -Djacoco.skip -Dspotless.check.skip && ./mvnw -pl worker spring-boot:run

seed: ## Seed dev catalog data into the compose postgres (dev/measurement only): make seed COURSES=10000
	docker compose exec -T postgres psql -q -U masternova -d masternova -v courses=$(or $(COURSES),200) < docs/db/seed-catalog.sql

web: ## Run Angular dev server (:4200, proxies /api to :8080)
	cd frontend && pnpm start

test: ## All tests: backend (unit + Testcontainers), pattern lab, frontend
	$(MVN) verify
	cd patterns/lab && ./mvnw -B -q test
	cd frontend && pnpm lint && pnpm test

format: ## Auto-format Java and TypeScript
	$(MVN) -q spotless:apply
	cd frontend && pnpm format

images: ## Build the three production images
	docker build -t masternova-spring/api:local --build-arg APP=api backend
	docker build -t masternova-spring/worker:local --build-arg APP=worker --build-arg PORT=8090 backend
	docker build -t masternova-spring/web:local frontend

scan: images ## Scan images for HIGH/CRITICAL CVEs with Trivy (runs in Docker)
	for img in api worker web; do \
	  docker run --rm -v /var/run/docker.sock:/var/run/docker.sock aquasec/trivy:latest image \
	    --severity HIGH,CRITICAL --ignore-unfixed masternova-spring/$$img:local; \
	done

E2E_RUN = cd e2e && pnpm install --frozen-lockfile && pnpm exec playwright install chromium && pnpm test
e2e: ## Browser e2e tests (Playwright) against the running stack: make stack && make e2e
	$(MAKE) seed COURSES=60
	$(E2E_RUN)

# ---- Kubernetes (Phase D4, patterns/devops/06): a local k3d cluster + its registry ----
# Images are tagged with the commit (immutable, like CI's :sha-…). A dirty tree adds a hash of the
# uncommitted changes: every edit gets its own tag, so the pods really roll (pullPolicy
# IfNotPresent would keep a re-pushed "-dirty" tag's old image). Computed on first use, then once:
# `make help` or `make up` never runs the diffs.
TAG ?= $(eval TAG := $(shell h=$$(git rev-parse --short=7 HEAD); \
  git diff --quiet HEAD -- backend frontend && echo sha-$$h \
  || echo sha-$$h-dirty-$$(git diff HEAD -- backend frontend | shasum | cut -c1-7)))$(TAG)
REGISTRY := localhost:5050

k8s-up: ## Create the local k3d cluster (3 nodes, registry on :5050, ingress on :8082/:8443)
	k3d cluster list masternova >/dev/null 2>&1 || k3d cluster create --config deploy/k3d/cluster.yaml
	kubectl config use-context k3d-masternova

k8s-down: ## Delete the local k3d cluster and its registry (all cluster data is lost)
	k3d cluster delete masternova

# Cluster add-ons, each in its own namespace, versions pinned (helm upgrade --install is idempotent)
k8s-platform: ## Install the cluster add-ons: Traefik config, cert-manager (+ local CA), CloudNativePG, KEDA, Argo CD
	kubectl apply -f deploy/k3d/traefik-config.yaml
	helm repo add kedacore https://kedacore.github.io/charts --force-update >/dev/null
	helm upgrade --install keda kedacore/keda --version 2.21.0 -n keda --create-namespace \
	  -f deploy/k3d/keda-values.yaml --wait
	helm repo add cnpg https://cloudnative-pg.github.io/charts --force-update >/dev/null
	helm upgrade --install cnpg cnpg/cloudnative-pg --version 0.29.1 -n cnpg-system --create-namespace --wait
	helm repo add jetstack https://charts.jetstack.io --force-update >/dev/null
	helm upgrade --install cert-manager jetstack/cert-manager --version v1.21.2 \
	  -n cert-manager --create-namespace --set crds.enabled=true --wait
	kubectl apply -f deploy/k3d/local-ca.yaml
	kubectl -n cert-manager wait certificate/masternova-local-ca --for=condition=Ready --timeout=60s
	helm repo add argo https://argoproj.github.io/argo-helm --force-update >/dev/null
	helm upgrade --install argocd argo/argo-cd --version 10.10.5 -n argocd --create-namespace \
	  -f deploy/k3d/argocd-values.yaml --wait
	@mkdir -p secrets && kubectl -n cert-manager get secret masternova-local-ca -o jsonpath='{.data.ca\.crt}' \
	  | base64 -d > secrets/masternova-local-ca.crt && echo "CA certificate → secrets/masternova-local-ca.crt (curl --cacert)"

k8s-images: images ## Build the three images and push them to the cluster's registry as :$(TAG) (GHCR's names)
	for img in api worker web; do \
	  docker tag masternova-spring/$$img:local $(REGISTRY)/masternova-spring-$$img:$(TAG) && \
	  docker push -q $(REGISTRY)/masternova-spring-$$img:$(TAG); \
	done

K8S_SECRETS_LOG = secrets/.k8s-apply.log
k8s-secrets: secrets ## Create the cluster's Secrets from the local secret files (never from git); restart pods if one changed
	@rm -f $(K8S_SECRETS_LOG)
	kubectl apply -f deploy/k3d/namespace.yaml
	kubectl -n masternova create secret generic masternova-app \
	  --from-file=JWT_ACCESS_SECRET=secrets/jwt_access_secret \
	  --from-file=NOTIFICATION_TOKEN_SECRET=secrets/notification_token_secret \
	  --from-file=PLAYBACK_TOKEN_SECRET=secrets/playback_token_secret \
	  --from-file=ENTITLEMENT_PLAYBACK_SECRET=secrets/entitlement_playback_secret \
	  --from-file=PROGRESS_BEACON_SECRET=secrets/progress_beacon_secret \
	  --dry-run=client -o yaml | kubectl apply -f - | tee -a $(K8S_SECRETS_LOG)
	kubectl -n masternova create secret generic masternova-db --type=kubernetes.io/basic-auth \
	  --from-literal=username=masternova --from-file=password=secrets/db_password \
	  --dry-run=client -o yaml | kubectl apply -f - | tee -a $(K8S_SECRETS_LOG)
	kubectl -n masternova create secret generic masternova-redis --from-file=password=secrets/redis_password \
	  --dry-run=client -o yaml | kubectl apply -f - | tee -a $(K8S_SECRETS_LOG)
	@# the read-only role KEDA queries with: CNPG creates it from the copy in masternova, KEDA
	@# reads the copy in its own namespace (it may read no other namespace's Secrets)
	@# (keda's namespace may not exist yet; masternova's comes from namespace.yaml — re-creating it
	@# here would drop its Pod Security labels)
	kubectl create namespace keda --dry-run=client -o yaml | kubectl apply -f - >/dev/null
	for ns in masternova keda; do \
	  kubectl -n $$ns create secret generic masternova-db-reader --type=kubernetes.io/basic-auth \
	    --from-literal=username=keda_reader --from-file=password=secrets/db_reader_password \
	    --dry-run=client -o yaml | kubectl apply -f - | tee -a $(K8S_SECRETS_LOG); \
	done
	@# Secrets are read once at JVM start (configtree), so a changed one restarts nothing by itself:
	@# roll the apps. (A rolling restart briefly mixes old- and new-key pods: rotate signing keys in
	@# a quiet moment.) The `restartedAt` annotation isn't in git, and Argo CD's diff ignores it.
	@if grep -q ' configured' $(K8S_SECRETS_LOG); then \
	  echo "a Secret changed: restarting api and worker"; \
	  kubectl -n masternova rollout restart deploy/api deploy/worker 2>/dev/null || true; \
	fi; rm -f $(K8S_SECRETS_LOG)

# The inner loop: both charts, the apps on locally built images. Argo CD deploys the same charts
# from git with GHCR images (D4.7). The infra release first: the apps' migration hook needs the DB.
k8s-deploy: k8s-secrets ## Deploy both Helm charts to the cluster, apps on the local images :$(TAG)
	helm upgrade --install masternova-infra deploy/helm/masternova-infra -n masternova \
	  -f deploy/helm/masternova-infra/values-local.yaml --wait --timeout 5m
	kubectl -n masternova wait cluster/masternova-db --for=condition=Ready --timeout=300s
	helm upgrade --install masternova deploy/helm/masternova -n masternova \
	  -f deploy/helm/masternova/values-local.yaml \
	  --set image.registry=registry.localhost:5050 --set image.tag=$(TAG) --wait --timeout 10m

k8s-gitops: ## Hand the cluster to Argo CD: the project + the root Application (app-of-apps) from main
	kubectl apply -f deploy/argocd/project.yaml -f deploy/argocd/root.yaml
	@echo "Argo CD UI: https://argocd.masternova.localhost:8443  (admin / \
	$$(kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath='{.data.password}' | base64 -d))"

k8s-bump: ## GitOps deploy: commit image tag TAG (must exist on GHCR) into the local values: make k8s-bump TAG=sha-abc1234
	deploy/argocd/bump.sh $(TAG)

KUBECONFORM_CACHE = $(or $(XDG_CACHE_HOME),$(HOME)/.cache)/kubeconform
HELM_SCHEMAS = -cache $(KUBECONFORM_CACHE) -schema-location default \
  -schema-location 'https://raw.githubusercontent.com/datreeio/CRDs-catalog/main/{{.Group}}/{{.ResourceKind}}_{{.ResourceAPIVersion}}.json'
helm-check: ## Lint both charts; validate what they render + the Argo CD manifests against the Kubernetes + CRD schemas (also in CI)
	@mkdir -p $(KUBECONFORM_CACHE)
	set -e; for c in masternova masternova-infra; do \
	  helm lint --strict deploy/helm/$$c -f deploy/helm/$$c/values-local.yaml; \
	  helm template $$c deploy/helm/$$c -n masternova -f deploy/helm/$$c/values-local.yaml \
	    | kubeconform -strict -summary $(HELM_SCHEMAS); \
	done
	kubeconform -strict -summary $(HELM_SCHEMAS) deploy/argocd/*.yaml deploy/argocd/apps/*.yaml deploy/k3d/namespace.yaml deploy/k3d/local-ca.yaml

k8s-seed: ## Seed dev catalog data into the cluster's postgres: make k8s-seed COURSES=60
	@# the CURRENT primary (after a failover it's no longer instance 1), as the superuser over the local socket
	kubectl -n masternova exec -i \
	  $$(kubectl -n masternova get pod -l cnpg.io/cluster=masternova-db,cnpg.io/instanceRole=primary -o name) \
	  -c postgres -- psql -q -d masternova -v courses=$(or $(COURSES),200) < docs/db/seed-catalog.sql

k8s-e2e: export BASE_URL = https://masternova.localhost:8443
k8s-e2e: export MAILPIT_URL = https://mail.masternova.localhost:8443
k8s-e2e: export E2E_LOCAL_CA = 1
k8s-e2e: ## Browser e2e tests against the cluster (https://masternova.localhost:8443)
	$(MAKE) k8s-seed COURSES=60
	$(E2E_RUN)

release: ## Tag and push a release from an up-to-date, clean main: make release VERSION=1.2.3
	@echo "$(VERSION)" | grep -Eq '^[0-9]+\.[0-9]+\.[0-9]+$$' || { echo "usage: make release VERSION=X.Y.Z"; exit 1; }
	@[ "$$(git rev-parse --abbrev-ref HEAD)" = main ] && git diff --quiet && git diff --cached --quiet \
	  || { echo "release from a clean main"; exit 1; }
	git pull --ff-only
	git tag -a "v$(VERSION)" -m "v$(VERSION)"
	git push origin "v$(VERSION)"

.PHONY: help up down nuke secrets stack obs obs-check api worker web test format images scan e2e k8s-up k8s-down k8s-images k8s-platform k8s-secrets k8s-deploy k8s-gitops k8s-bump helm-check k8s-seed k8s-e2e release
