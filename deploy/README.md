# deploy/

Each folder is filled by a DevOps phase in [`ROADMAP.md`](../ROADMAP.md).

| Folder | Phase | What lands here |
|---|---|---|
| `observability/` | D3 | Prometheus scrape config + alert rules, Grafana datasources and dashboards (provisioned as code), Tempo, Loki, Alloy, Alertmanager — the compose `observability` profile (`make obs`) |
| `k3d/` | D4.1–D4.7 | The local cluster (`make k8s-up`): 3 nodes, a registry on :5050, Traefik on :8082/:8443 (a DaemonSet keeping the client IP), the namespace (Pod Security labels); the add-ons' config (`make k8s-platform`): local CA, KEDA (Secrets restricted), Argo CD |
| `helm/masternova/` | D4.5 | The apps' chart: api, worker, web, ConfigMap, Ingress, the `db-migrate` pre-upgrade hook (`make k8s-deploy`, `make helm-check`) |
| `helm/masternova-infra/` | D4.5 | The data layer's chart: CNPG `Cluster`, Redis (vendored dependency), SeaweedFS, Mailpit |
| `argocd/` | D4.7 | Argo CD: the AppProject fence, the root Application (app-of-apps, `make k8s-gitops`) and its two children (infra wave 0, apps wave 1). Deploy = `make k8s-bump TAG=sha-…` (checks GHCR, commits the tag as you), then a PR |
| `terraform/` | D6 (optional) | AWS: VPC, ECR, ECS Fargate + ALB, RDS, ElastiCache, S3 + CloudFront, budget alarm |
