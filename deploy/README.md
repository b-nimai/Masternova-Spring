# deploy/

Empty on purpose. Each folder is filled by a DevOps phase in [`ROADMAP.md`](../ROADMAP.md).

| Folder | Phase | What lands here |
|---|---|---|
| `helm/masternova/` | D4 | Helm chart: api, worker and web Deployments, Services, Ingress, HPA, ConfigMap/Secret, probes |
| `argocd/` | D4 | Argo CD `Application` manifest. CI bumps the image tag in the chart values, then Argo syncs the cluster from git (GitOps) |
| `terraform/` | D6 (optional) | AWS: VPC, ECR, ECS Fargate + ALB, RDS, ElastiCache, S3 + CloudFront, budget alarm |
