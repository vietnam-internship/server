1. Kubernetes deployment — PR #10 [https://github.com/vietnam-internship/server/pull/10]
- Kustomize vs Helm: Kustomize is the right choice at your current scale (2 components) — no need to switch. In practice the decision isn't driven by how many components you write yourself, but by whether you need to install third-party infrastructure (ingress, cert-manager, monitoring, etc.), since those are almost always distributed as Helm charts. So the common pattern on larger teams is to use both: Helm to install third-party charts, Kustomize to overlay your own app per environment. You don't need to commit to one side now.
  Pitfalls worth knowing before going further:
* Secrets are currently created by hand with kubectl create secret for prod — no audit trail, no rollback through git. Move to a declarative mechanism (External Secrets Operator or Sealed Secrets) before the number of secrets grows.
* Running MySQL as a Deployment in overlays/local is fine for dev only. If you ever self-host the DB in-cluster for real prod you'd need a StatefulSet + PVC — but better to use a managed DB (RDS, Cloud SQL) if you can. Self-hosting a database in Kubernetes is the single biggest source of risk going to production.
  One thing to fix so the manifests actually apply: the overlays reference a configmap.env that isn't committed, so kubectl apply -k fails on a fresh checkout. It's not a secret, so either commit it or inline the values as literals: in the kustomization; keep only secret.env gitignored. (And pin the prod image to a SHA/tag instead of latest.)
  We've also put together a production deployment checklist for reference — you won't need most of it for the MVP, but it's a useful map of what "production-ready" looks like:
+ Container & Orchestration
* Kubernetes cluster (self-managed or managed: EKS/GKE/AKS)
* Kustomize/Helm to manage manifests per environment
* Container registry (Docker Hub, ECR, GCR, Harbor) with image scanning (Trivy, Snyk)
+ CI/CD
* Build-test-deploy pipeline (GitHub Actions, GitLab CI, Jenkins)
* GitOps controller (ArgoCD, Flux) to auto-sync instead of manual kubectl apply
* Automated tests (unit, integration) run before deploy
+ Secrets & Config
* Secret management: External Secrets Operator, Sealed Secrets, or Vault
* ConfigMap/Secret separated per environment (already done via Kustomize overlays)
* Never commit plaintext secrets to git, even on a private branch
+ Networking
* Ingress controller (nginx-ingress, Traefik) + TLS (cert-manager + Let's Encrypt)
* NetworkPolicy to restrict traffic between namespaces/pods
* Service mesh (Istio, Linkerd) — only needed when you have many services and need mTLS/traffic control
+ Observability
* Centralized logging: Loki, ELK/EFK stack
* Metrics: Prometheus + Grafana
* Tracing: Jaeger, Tempo (important once many services call each other)
* Alerting: Alertmanager, PagerDuty/Opsgenie
+ Database & Storage
* Managed DB (RDS, Cloud SQL) instead of self-hosting MySQL in-cluster if possible
* If self-hosting: StatefulSet + PVC + automated backups (Velero, mysqldump cron)
* Test backup & restore regularly — don't just back up without ever testing a restore
+ Reliability & Scaling
* Resource requests/limits on every container
* Liveness/readiness/startup probes
* HPA (Horizontal Pod Autoscaler) on CPU/memory or custom metrics
* PodDisruptionBudget to avoid downtime during node maintenance
* Multi-replica for every critical service (avoid a single point of failure)
+ Security
* RBAC per namespace, principle of least privilege
* Pod Security Standards (restricted mode)
* Fixed image tags (SHA or semver), no latest
* Network egress/ingress policy, regular vulnerability scans
+ Operations
* Runbook/incident response for common incidents
* A staging environment as close to prod as possible to test before real deploys
* A clear rollback strategy (Helm rollback, ArgoCD rollback, or blue-green/canary)