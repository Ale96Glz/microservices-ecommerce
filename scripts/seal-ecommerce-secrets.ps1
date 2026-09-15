#!/usr/bin/env pwsh
#requires -Version 5.1

# Cifra el Secret ecommerce-secrets del cluster y genera/actualiza el archivo
# k8s/sealed-ecommerce-secrets.yaml (versionado en git).
# El SealedSecret NO contiene valores en claro: solo lo descifra el controller
# sealed-secrets dentro del cluster (ver ADR-0009).
#
# Requisitos:
#   - kubectl y kubeseal en el PATH (o KUBESEAL apuntando al binario).
#   - Controller sealed-secrets corriendo en el cluster (kube-system).

$ErrorActionPreference = "Stop"

$namespace = "ecommerce"
$repoRoot = Split-Path -Parent $PSScriptRoot
$kubesealPath = Join-Path $repoRoot "tools"
$kubesealExe = if ($env:KUBESEAL) {
    $env:KUBESEAL
}
else {
    $candidate = Join-Path $kubesealPath "kubeseal\kubeseal.exe"
    if (Test-Path $candidate) {
        $candidate
    }
    else {
        "kubeseal"
    }
}

if (-not (Get-Command kubectl -ErrorAction SilentlyContinue)) {
    throw "kubectl no está disponible en el PATH."
}
if (-not (Get-Command $kubesealExe -ErrorAction SilentlyContinue) -and -not (Test-Path $kubesealExe)) {
    throw "kubeseal no está disponible. Descárgalo en $kubesealPath\kubeseal\ o define KUBESEAL."
}

$context = kubectl config current-context
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($context)) {
    throw "No hay un contexto Kubernetes activo."
}
Write-Host "Contexto actual: $context" -ForegroundColor Cyan

& kubectl get secret ecommerce-secrets -n $namespace -o name 2>$null | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw "No existe el Secret ecommerce-secrets en el namespace $namespace."
}

Write-Host "Leyendo y sellando ecommerce-secrets..." -ForegroundColor Cyan
$raw = kubectl get secret ecommerce-secrets -n $namespace -o yaml
if ($LASTEXITCODE -ne 0) {
    throw "No se pudo leer el Secret."
}

$sealed = $raw | & $kubesealExe --format yaml --name ecommerce-secrets --namespace $namespace
if ($LASTEXITCODE -ne 0) {
    throw "kubeseal falló con código $LASTEXITCODE."
}

$out = Join-Path (Split-Path -Parent $PSScriptRoot) "k8s\sealed-ecommerce-secrets.yaml"
$text = ($sealed -join "`r`n")
[IO.File]::WriteAllText($out, $text, [Text.UTF8Encoding]::new($false))

Write-Host "SealedSecret generado en $out" -ForegroundColor Green
Write-Host "Revísalo y haz commit; NO contiene valores en claro." -ForegroundColor DarkGray

& kubectl apply -f $out
if ($LASTEXITCODE -ne 0) {
    throw "No se pudo aplicar el SealedSecret."
}