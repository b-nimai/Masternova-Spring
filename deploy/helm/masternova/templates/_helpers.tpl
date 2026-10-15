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
Pod-level settings shared by the JVM pods (api, worker, migrate Job). The image's `USER app` is a
name the kubelet can't check against runAsNonRoot, so the numeric ids (Dockerfile's adduser).
Service links off: a Service named `api` would inject API_PORT=tcp://…, which server.port reads.
*/}}
{{- define "masternova.jvmPodSpec" -}}
enableServiceLinks: false
automountServiceAccountToken: false
securityContext:
  runAsNonRoot: true
  runAsUser: 100
  runAsGroup: 101
  fsGroup: 101
  seccompProfile: { type: RuntimeDefault }
{{- end }}

{{- define "masternova.restrictedContainer" -}}
securityContext:
  readOnlyRootFilesystem: true
  allowPrivilegeEscalation: false
  capabilities: { drop: [ALL] }
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
