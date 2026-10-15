# deploy/

Each folder is filled by a DevOps phase in [`ROADMAP.md`](../ROADMAP.md).

| Folder | Phase | What lands here |
|---|---|---|
| `observability/` | D3 | Prometheus scrape config + alert rules, Grafana datasources and dashboards (provisioned as code), Tempo, Loki, Alloy, Alertmanager — the compose `observability` profile (`make obs`) |
| `k3d/` | D4.1 | The local cluster's definition (`make k8s-up`): 3 nodes, a registry on :5050, Traefik on :8082/:8443 |
| `k8s/` | D4.2 | Raw manifests, written by hand before the chart (`make k8s-apply`) |
| `helm/masternova/` | D4 | Helm chart: api, worker and web Deployments, Services, Ingress, HPA, ConfigMap/Secret, probes |
| `argocd/` | D4 | Argo CD `Application` manifest. CI bumps the image tag in the chart values, then Argo syncs the cluster from git (GitOps) |
| `terraform/` | D6 (optional) | AWS: VPC, ECR, ECS Fargate + ALB, RDS, ElastiCache, S3 + CloudFront, budget alarm |
