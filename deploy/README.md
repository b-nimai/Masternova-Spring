# deploy/

Each folder is filled by a DevOps phase in [`ROADMAP.md`](../ROADMAP.md).

| Folder | Phase | What lands here |
|---|---|---|
| `observability/` | D3 | Prometheus scrape config + alert rules, Grafana datasources and dashboards (provisioned as code), Tempo, Loki, Alloy, Alertmanager — the compose `observability` profile (`make obs`) |
| `k3d/` | D4.1, D4.3 | The local cluster (`make k8s-up`): 3 nodes, a registry on :5050, Traefik on :8082/:8443 (a DaemonSet keeping the client IP), the local CA (`make k8s-platform`) |
| `k8s/` | D4.2–D4.4 | Raw manifests, written by hand before the chart (`make k8s-apply`): apps, ingress, CNPG `Cluster`, Redis chart values, the `db-migrate` Job |
| `helm/masternova/` | D4 | Helm chart: api, worker and web Deployments, Services, Ingress, HPA, ConfigMap/Secret, probes |
| `argocd/` | D4 | Argo CD `Application` manifest. CI bumps the image tag in the chart values, then Argo syncs the cluster from git (GitOps) |
| `terraform/` | D6 (optional) | AWS: VPC, ECR, ECS Fargate + ALB, RDS, ElastiCache, S3 + CloudFront, budget alarm |
