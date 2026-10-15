# deploy/

Each folder is filled by a DevOps phase in [`ROADMAP.md`](../ROADMAP.md).

| Folder | Phase | What lands here |
|---|---|---|
| `observability/` | D3 | Prometheus scrape config + alert rules, Grafana datasources and dashboards (provisioned as code), Tempo, Loki, Alloy, Alertmanager — the compose `observability` profile (`make obs`) |
| `k3d/` | D4.1, D4.3 | The local cluster (`make k8s-up`): 3 nodes, a registry on :5050, Traefik on :8082/:8443 (a DaemonSet keeping the client IP), the namespace (Pod Security labels), the local CA (`make k8s-platform`) |
| `helm/masternova/` | D4.5 | The apps' chart: api, worker, web, ConfigMap, Ingress, the `db-migrate` pre-upgrade hook (`make k8s-deploy`, `make helm-check`) |
| `helm/masternova-infra/` | D4.5 | The data layer's chart: CNPG `Cluster`, Redis (vendored dependency), SeaweedFS, Mailpit |
| `argocd/` | D4 | Argo CD `Application` manifest. CI bumps the image tag in the chart values, then Argo syncs the cluster from git (GitOps) |
| `terraform/` | D6 (optional) | AWS: VPC, ECR, ECS Fargate + ALB, RDS, ElastiCache, S3 + CloudFront, budget alarm |
