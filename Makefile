# Day-to-day commands. `make help` lists them.
.DEFAULT_GOAL := help
MVN := cd backend && ./mvnw -B

help: ## List targets
	@grep -E '^[a-z-]+:.*## ' $(MAKEFILE_LIST) | awk -F':.*## ' '{printf "  \033[36m%-14s\033[0m %s\n", $$1, $$2}'

up: ## Start infra (postgres, redis, minio, mailpit)
	docker compose up -d --wait postgres redis minio mailpit && docker compose up minio-init

down: ## Stop everything (volumes kept)
	docker compose --profile app down

nuke: ## Stop everything AND delete volumes (fresh database)
	docker compose --profile app down -v

stack: ## Build images and run the whole app in containers (web on :8081)
	docker compose --profile app up -d --build --wait postgres redis minio mailpit api worker web

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

.PHONY: help up down nuke stack api worker web test format images scan
