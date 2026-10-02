# Day-to-day commands. `make help` lists them.
.DEFAULT_GOAL := help
MVN := cd backend && ./mvnw -B

help: ## List targets
	@grep -E '^[a-z-]+:.*## ' $(MAKEFILE_LIST) | awk -F':.*## ' '{printf "  \033[36m%-14s\033[0m %s\n", $$1, $$2}'

up: ## Start infra (postgres, redis, mailpit); S3 storage is the opt-in `media` profile until Phase 7
	docker compose up -d --wait postgres redis mailpit

down: ## Stop everything (volumes kept)
	docker compose --profile app down

nuke: ## Stop everything AND delete volumes (fresh database)
	docker compose --profile app down -v

secrets: ## Generate the local secret files the container stack mounts (gitignored, once per machine)
	@mkdir -p secrets
	@[ -s secrets/jwt_access_secret ] || { head -c 48 /dev/urandom | base64 | tr -d '\n' > secrets/jwt_access_secret; \
	  chmod 644 secrets/jwt_access_secret; echo "created secrets/jwt_access_secret"; }

stack: secrets ## Build images and run the whole app in containers (web on :8081)
	docker compose --profile app up -d --build --wait postgres redis mailpit api worker web

api: ## Run the api from source (:8080)
	$(MVN) -q install -DskipTests -Djacoco.skip -Dspotless.check.skip && cd backend && ./mvnw -pl api spring-boot:run

worker: ## Run the worker from source (:8090)
	$(MVN) -q install -DskipTests -Djacoco.skip -Dspotless.check.skip && cd backend && ./mvnw -pl worker spring-boot:run

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

e2e: ## Browser e2e tests (Playwright) against the running stack: make stack && make e2e
	cd e2e && pnpm install --frozen-lockfile && pnpm exec playwright install chromium && pnpm test

release: ## Tag and push a release from an up-to-date, clean main: make release VERSION=1.2.3
	@echo "$(VERSION)" | grep -Eq '^[0-9]+\.[0-9]+\.[0-9]+$$' || { echo "usage: make release VERSION=X.Y.Z"; exit 1; }
	@[ "$$(git rev-parse --abbrev-ref HEAD)" = main ] && git diff --quiet && git diff --cached --quiet \
	  || { echo "release from a clean main"; exit 1; }
	git pull --ff-only
	git tag -a "v$(VERSION)" -m "v$(VERSION)"
	git push origin "v$(VERSION)"

.PHONY: help up down nuke secrets stack api worker web test format images scan e2e release
