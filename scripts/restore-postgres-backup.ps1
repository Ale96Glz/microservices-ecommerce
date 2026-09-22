#!/usr/bin/env pwsh
#requires -Version 5.1

# Restaura un dump custom del PVC postgres-backup-pvc a una base temporal
# (${Database}_restore_drill), lista tablas y borra esa base.
# No toca las bases vivas. Requiere kubectl y el Secret ecommerce-secrets.
#
#   .\scripts\restore-postgres-backup.ps1
#   .\scripts\restore-postgres-backup.ps1 -Database pagos_db
#   .\scripts\restore-postgres-backup.ps1 -Stamp 20260922T183654Z

param(
    [string]$Namespace = "ecommerce",
    [ValidateSet("auth_db", "catalogo_db", "pedidos_db", "pagos_db", "notificaciones_db")]
    [string]$Database = "catalogo_db",
    [string]$Stamp = ""
)

$ErrorActionPreference = "Stop"
$jobName = "postgres-restore-drill"
$restoreDb = "${Database}_restore_drill"

$stampLine = if ([string]::IsNullOrWhiteSpace($Stamp)) {
    "STAMP=`$(ls -1 /backups | sort | tail -1)"
} else {
    "STAMP='$Stamp'"
}

$yaml = @"
apiVersion: batch/v1
kind: Job
metadata:
  name: $jobName
  namespace: $Namespace
spec:
  backoffLimit: 1
  ttlSecondsAfterFinished: 300
  template:
    spec:
      restartPolicy: Never
      containers:
        - name: pg-restore
          image: postgres:16-alpine
          imagePullPolicy: IfNotPresent
          env:
            - name: PGHOST
              value: postgres
            - name: PGUSER
              valueFrom:
                secretKeyRef:
                  name: ecommerce-secrets
                  key: POSTGRES_USER
            - name: PGPASSWORD
              valueFrom:
                secretKeyRef:
                  name: ecommerce-secrets
                  key: POSTGRES_PASSWORD
          command:
            - sh
            - -c
            - |
              set -euo pipefail
              echo "Dumps en el PVC:"
              find /backups -type f -name "*.dump" | sort
              __STAMP_LINE__
              DUMP="/backups/`${STAMP}/$Database.dump"
              echo "Restaurando `${DUMP} -> $restoreDb"
              [ -f "`${DUMP}" ] || { echo "No existe el dump"; exit 1; }
              n=`$(find "/backups/`${STAMP}" -maxdepth 1 -name '*.dump' | wc -l)
              if [ "`${n}" -lt 5 ]; then
                echo "Stamp `${STAMP} incompleto (`${n} dumps); abortando"
                exit 1
              fi
              psql -d postgres -v ON_ERROR_STOP=1 -c "DROP DATABASE IF EXISTS $restoreDb WITH (FORCE);"
              psql -d postgres -v ON_ERROR_STOP=1 -c "CREATE DATABASE $restoreDb;"
              pg_restore --dbname=$restoreDb --no-owner --no-acl --exit-on-error "`${DUMP}"
              echo "Tablas restauradas:"
              psql -d $restoreDb -c "\dt"
              psql -d postgres -v ON_ERROR_STOP=1 -c "DROP DATABASE $restoreDb WITH (FORCE);"
              echo "RESTORE DRILL OK (base temporal eliminada; $Database no se toco)"
          volumeMounts:
            - name: backups
              mountPath: /backups
              readOnly: true
      volumes:
        - name: backups
          persistentVolumeClaim:
            claimName: postgres-backup-pvc
            readOnly: true
"@

$yaml = $yaml.Replace("__STAMP_LINE__", $stampLine)

kubectl delete job $jobName -n $Namespace --ignore-not-found | Out-Null
$jobTmp = Join-Path ([IO.Path]::GetTempPath()) ("postgres-restore-drill-{0}.yaml" -f [guid]::NewGuid().ToString("n"))
try {
    [IO.File]::WriteAllText($jobTmp, $yaml, [Text.UTF8Encoding]::new($false))
    & kubectl apply -f $jobTmp
    if ($LASTEXITCODE -ne 0) { throw "kubectl apply falló." }
    & kubectl wait -n $Namespace --for=condition=complete "job/$jobName" --timeout=120s
    if ($LASTEXITCODE -ne 0) { throw "El Job de restore no completó a tiempo." }
    & kubectl logs -n $Namespace "job/$jobName"
}
finally {
    kubectl delete job $jobName -n $Namespace --ignore-not-found | Out-Null
    Remove-Item -LiteralPath $jobTmp -Force -ErrorAction SilentlyContinue
}
