{{/* Labels every object carries; `component` is api | worker | web | db-migrate. */}}
{{- define "masternova.labels" -}}
app.kubernetes.io/name: {{ .component }}
app.kubernetes.io/instance: {{ .root.Release.Name }}
app.kubernetes.io/part-of: masternova
app.kubernetes.io/version: {{ .root.Values.image.tag | quote }}
app.kubernetes.io/managed-by: {{ .root.Release.Service }}
helm.sh/chart: {{ printf "%s-%s" .root.Chart.Name .root.Chart.Version }}
{{- end }}

{{/* The selector: only labels that never change (a Deployment's selector is immutable). */}}
{{- define "masternova.selectorLabels" -}}
app.kubernetes.io/name: {{ .component }}
app.kubernetes.io/instance: {{ .root.Release.Name }}
{{- end }}

{{/* <registry>/masternova-spring-<app>:<tag>, the names CI publishes to GHCR. */}}
{{- define "masternova.image" -}}
{{- $tag := required "image.tag is required: an immutable sha-<commit> or X.Y.Z" .root.Values.image.tag -}}
{{ printf "%s/masternova-spring-%s:%s" .root.Values.image.registry .app $tag }}
{{- end }}

{{/*
Pod-level settings every pod shares. `uid`/`gid` (optional): run as that numeric user — the JVM
images' `USER app` is a name the kubelet can't check against runAsNonRoot (Dockerfile's adduser:
100/101). Without them the image's own user (nginx's master starts as root, then drops).
Service links off: a Service named `api` would inject API_PORT=tcp://…, which server.port reads.
*/}}
{{- define "masternova.podSpec" -}}
enableServiceLinks: false
automountServiceAccountToken: false
securityContext:
  {{- with .uid }}
  runAsNonRoot: true
  runAsUser: {{ . }}
  runAsGroup: {{ $.gid }}
  fsGroup: {{ $.gid }}
  {{- end }}
  seccompProfile: { type: RuntimeDefault }
{{- end }}

{{/* The JVM pods (api, worker, migrate Job): the app user from the Dockerfile. */}}
{{- define "masternova.jvmPodSpec" -}}
{{ include "masternova.podSpec" (dict "uid" 100 "gid" 101) }}
{{- end }}

{{/* `add` (optional): capabilities the container needs back after dropping ALL. */}}
{{- define "masternova.restrictedContainer" -}}
securityContext:
  readOnlyRootFilesystem: true
  allowPrivilegeEscalation: false
  capabilities:
    drop: [ALL]
    {{- with .add }}
    add: [{{ join ", " . }}]
    {{- end }}
{{- end }}

{{/* One replica per node where possible: a drain or a node failure takes at most one. */}}
{{- define "masternova.spreadAcrossNodes" -}}
topologySpreadConstraints:
  - maxSkew: 1
    topologyKey: kubernetes.io/hostname
    whenUnsatisfiable: ScheduleAnyway
    labelSelector:
      matchLabels: {{- include "masternova.selectorLabels" . | nindent 8 }}
{{- end }}

{{/*
The JVM apps' probes. startup: booted yet? · liveness: wedged → restart · readiness: send
traffic? Liveness never checks a dependency: a DB outage must not restart every pod.
`readinessPeriod` / `readinessFailures`: how fast an unready pod leaves the endpoints.
*/}}
{{- define "masternova.jvmProbes" -}}
startupProbe:
  httpGet: { path: /actuator/health/liveness, port: http }
  periodSeconds: 2
  failureThreshold: 30
livenessProbe:
  httpGet: { path: /actuator/health/liveness, port: http }
  periodSeconds: 10
readinessProbe:
  httpGet: { path: /actuator/health/readiness, port: http }
  periodSeconds: {{ .readinessPeriod }}
  failureThreshold: {{ .readinessFailures | default 3 }}
{{- end }}

{{/* A writable /tmp on a read-only root filesystem (the JVM's and Tomcat's scratch files). */}}
{{- define "masternova.tmpVolume" -}}
- name: tmp
  emptyDir: { medium: Memory, sizeLimit: 64Mi }
{{- end }}

{{/*
/run/secrets as ONE projected volume from several Secrets, a file per key (Spring's configtree).
`appKeys` = the keys of the app Secret this pod may read (least privilege).
*/}}
{{- define "masternova.secretsVolume" -}}
- name: secrets
  projected:
    sources:
      {{- with .appKeys }}
      - secret:
          name: {{ $.root.Values.secrets.app }}
          items:
            {{- range . }}
            - { key: {{ . }}, path: {{ . }} }
            {{- end }}
      {{- end }}
      - secret:
          name: {{ .root.Values.secrets.db }}
          items:
            - { key: username, path: DATABASE_USER }
            - { key: password, path: DATABASE_PASSWORD }
      {{- if .redis }}
      - secret: # the file name IS the property name
          name: {{ .root.Values.secrets.redis }}
          items: [{ key: password, path: spring.data.redis.password }]
      {{- end }}
{{- end }}

{{/*
Restarts pods when the config changes: env is read at start, so a ConfigMap edit alone does nothing.
The hash covers the DATA only (not the ConfigMap's labels, which carry the image tag).
*/}}
{{- define "masternova.configChecksum" -}}
checksum/config: {{ toJson .Values.config | sha256sum }}
{{- end }}

{{/*
The database the apps use, parsed from config.DATABASE_URL (jdbc:postgresql://host:port/name) so
it is written once: db-grants connects to it, KEDA queries it. A dict: host, port, name.
*/}}
{{- define "masternova.database" -}}
{{- $u := urlParse (trimPrefix "jdbc:" .Values.config.DATABASE_URL) -}}
{{- $hp := splitList ":" $u.host -}}
{{- toJson (dict "host" (first $hp) "port" (ternary (last $hp) "5432" (gt (len $hp) 1)) "name" (trimPrefix "/" $u.path)) -}}
{{- end }}
