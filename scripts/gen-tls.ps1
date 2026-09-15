#!/usr/bin/env pwsh
#requires -Version 5.1

# Genera una CA de desarrollo y un certificado TLS para ecommerce.local,
# y guarda el certificado como Secret TLS `ecommerce-tls` en el cluster.
# Uso: ./scripts/gen-tls.ps1 [hostname]   (default: ecommerce.local)
# Requiere openssl y kubectl disponibles en el PATH.

$ErrorActionPreference = "Stop"

$hostname = if ($args.Count -gt 0) { $args[0] } else { "ecommerce.local" }
$namespace = "ecommerce"
$repoRoot = Split-Path -Parent $PSScriptRoot
$certsDir = Join-Path $repoRoot "certs"

foreach ($binary in @("openssl", "kubectl")) {
    if (-not (Get-Command $binary -ErrorAction SilentlyContinue)) {
        throw "'$binary' no está disponible en el PATH."
    }
}

$opensslConf = $env:OPENSSL_CONF
if (-not $opensslConf -or -not (Test-Path $opensslConf)) {
    $candidates = @(
        "C:\Program Files\Git\mingw64\etc\ssl\openssl.cnf",
        "C:\Program Files\Git\usr\ssl\openssl.cnf",
        "/usr/lib/ssl/openssl.cnf"
    )
    $foundConf = $candidates | Where-Object { Test-Path $_ } | Select-Object -First 1
    if ($foundConf) {
        $env:OPENSSL_CONF = $foundConf
        Write-Host "Usando OPENSSL_CONF=$foundConf" -ForegroundColor DarkGray
    }
}

$context = kubectl config current-context
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($context)) {
    throw "No hay un contexto Kubernetes activo."
}
Write-Host "Contexto actual: $context" -ForegroundColor Cyan

if (-not (Test-Path $certsDir)) {
    New-Item -ItemType Directory -Path $certsDir | Out-Null
}

$caKey = Join-Path $certsDir "ca-ecommerce.key"
$caCert = Join-Path $certsDir "ca-ecommerce.crt"
$serverKey = Join-Path $certsDir "ecommerce.key"
$serverCsr = Join-Path $certsDir "ecommerce.csr"
$serverCert = Join-Path $certsDir "ecommerce.crt"

Write-Host ""
Write-Host "[1/4] Generando CA de desarrollo" -ForegroundColor Cyan
if (-not (Test-Path $caKey) -or -not (Test-Path $caCert)) {
    & openssl req -x509 -nodes -newkey rsa:2048 -sha256 -days 3650 `
        -keyout $caKey -out $caCert `
        -subj "/CN=ecommerce-dev-ca"
    if ($LASTEXITCODE -ne 0) { throw "No se pudo generar la CA." }
    Write-Host "CA creada: $caCert" -ForegroundColor Green
}
else {
    Write-Host "Reutilizando CA existente." -ForegroundColor Yellow
}

Write-Host ""
Write-Host "[2/4] Generando certificado para $hostname" -ForegroundColor Cyan
& openssl req -newkey rsa:2048 -nodes -keyout $serverKey -out $serverCsr `
    -subj "/CN=$hostname"
if ($LASTEXITCODE -ne 0) { throw "No se pudo generar la clave/csr." }

$extFile = Join-Path $certsDir "ecommerce.ext"
@(
    "authorityKeyIdentifier=keyid,issuer"
    "basicConstraints=CA:FALSE"
    "keyUsage=digitalSignature,keyEncipherment"
    "extendedKeyUsage=serverAuth"
    "subjectAltName=DNS:$hostname,DNS:localhost,IP:127.0.0.1"
) | Set-Content -Path $extFile -Encoding ASCII

& openssl x509 -req -in $serverCsr -CA $caCert -CAkey $caKey -CAcreateserial `
    -out $serverCert -days 825 -sha256 -extfile $extFile
if ($LASTEXITCODE -ne 0) { throw "No se pudo firmar el certificado." }
Write-Host "Certificado creado: $serverCert" -ForegroundColor Green

Write-Host ""
Write-Host "[3/4] Guardando Secret TLS en Kubernetes" -ForegroundColor Cyan
& kubectl create secret tls ecommerce-tls `
    --namespace $namespace `
    --cert $serverCert `
    --key $serverKey `
    --dry-run=client -o yaml | kubectl apply -f -
if ($LASTEXITCODE -ne 0) { throw "No se pudo crear el Secret ecommerce-tls." }
Write-Host "Secret ecommerce-tls actualizado." -ForegroundColor Green

Write-Host ""
Write-Host "[4/4] Instrucciones para el consumo local" -ForegroundColor Cyan
Write-Host "1. Agrega '$hostname' al archivo de hosts apuntando al nodo/Ingress:"
Write-Host "   - Windows: C:\Windows\System32\drivers\etc\hosts"
Write-Host "     $hostname = IP del LoadBalancer del ingress-nginx (kubectl get svc -n ingress-nginx)"
Write-Host "   - Para el navegador/curl, confía en la CA de desarrollo:"
Write-Host "     $caCert"
Write-Host ""
Write-Host "Verifica con: curl https://$hostname/actuator/health --cacert $caCert" -ForegroundColor DarkGray

$null = kubectl get secret ecommerce-tls -n $namespace -o name
if ($LASTEXITCODE -ne 0) {
    throw "No se encontró el Secret ecommerce-tls."
}
Write-Host ""
Write-Host "TLS listo. Secret ecommerce-tls presente en el namespace $namespace." -ForegroundColor Green