{{/*
Common helpers for the telco-service chart.
*/}}

{{/* Fail fast if the required serviceName is missing. */}}
{{- define "telco-service.serviceName" -}}
{{- required "serviceName is required (set it in the per-service values file)" .Values.serviceName -}}
{{- end -}}

{{/* Workload / Service / DNS name - identical to the compose service name. */}}
{{- define "telco-service.fullname" -}}
{{- include "telco-service.serviceName" . -}}
{{- end -}}

{{/* Chart name-version label value. */}}
{{- define "telco-service.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/* ServiceAccount name. */}}
{{- define "telco-service.serviceAccountName" -}}
{{- if .Values.serviceAccount.name -}}
{{- .Values.serviceAccount.name -}}
{{- else -}}
{{- include "telco-service.fullname" . -}}
{{- end -}}
{{- end -}}

{{/* ConfigMap / Secret names. */}}
{{- define "telco-service.configMapName" -}}
{{- printf "%s-config" (include "telco-service.fullname" .) -}}
{{- end -}}
{{- define "telco-service.secretName" -}}
{{- printf "%s-secret" (include "telco-service.fullname" .) -}}
{{- end -}}

{{/* Container image reference. */}}
{{- define "telco-service.image" -}}
{{- $repo := .Values.image.repository | default (printf "telco-%s" (include "telco-service.serviceName" .)) -}}
{{- printf "%s/%s/%s:%s" .Values.image.registry .Values.image.owner $repo (.Values.image.tag | toString) -}}
{{- end -}}

{{/* Common labels applied to every object. */}}
{{- define "telco-service.labels" -}}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/part-of: telco-crm
app.kubernetes.io/name: {{ include "telco-service.fullname" . }}
app.kubernetes.io/component: {{ include "telco-service.fullname" . }}
app.kubernetes.io/version: {{ .Values.image.tag | toString | quote }}
helm.sh/chart: {{ include "telco-service.chart" . }}
{{- end -}}

{{/*
Selector labels - only the stable identity label, never chart/version, so
rolling updates keep matching the same Pods.
*/}}
{{- define "telco-service.selectorLabels" -}}
app.kubernetes.io/name: {{ include "telco-service.fullname" . }}
{{- end -}}
