{{/*
Common helpers for the telco-dependencies chart.
*/}}

{{/* Chart name-version label value. */}}
{{- define "telco-deps.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/*
Common labels applied to every object. Kept minimal and stable so that the app
charts (15.2.x) and operators can select dependencies deterministically.
*/}}
{{- define "telco-deps.labels" -}}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/part-of: telco-crm
helm.sh/chart: {{ include "telco-deps.chart" . }}
{{- end -}}

{{/*
Per-component labels. Call with a dict: (dict "ctx" $ "component" "postgres").
The component name doubles as app.kubernetes.io/name so the Service selector and
the workload pod template agree on a single, compose-identical label.
*/}}
{{- define "telco-deps.componentLabels" -}}
{{- $ctx := .ctx -}}
{{ include "telco-deps.labels" $ctx }}
app.kubernetes.io/name: {{ .component }}
app.kubernetes.io/component: {{ .component }}
{{- end -}}

{{/*
Selector labels for a component. Call with (dict "component" "postgres").
Only the stable identity labels - never chart/version - so rolling updates keep
matching the same Pods.
*/}}
{{- define "telco-deps.selectorLabels" -}}
app.kubernetes.io/name: {{ .component }}
{{- end -}}
