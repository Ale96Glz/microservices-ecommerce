#!/usr/bin/env pwsh
#requires -Version 5.1

# Compatible con Windows PowerShell 5.1 y PowerShell 7 en Windows, Linux y macOS.
# Requiere kubectl disponible en el PATH.

$ErrorActionPreference = "Stop"

$namespace = "ecommerce"
$repoRoot = Split-Path -Parent $PSScriptRoot
$k8sPath = Join-Path $repoRoot "k8s"

function Invoke-Kubectl {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Arguments
    )

    & kubectl @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "kubectl falló con código $LASTEXITCODE."
    }
}

function ConvertFrom-SecureStringValue {
    param(
        [Parameter(Mandatory = $true)]
        [Security.SecureString]$Value
    )

    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($Value)
    try {
        return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    }
    finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    }
}

function Read-RequiredSecret {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Prompt
    )

    while ($true) {
        $secureValue = Read-Host -Prompt $Prompt -AsSecureString
        $plainValue = ConvertFrom-SecureStringValue $secureValue
        if (-not [string]::IsNullOrWhiteSpace($plainValue)) {
            return $plainValue
        }
        Write-Host "El valor no puede estar vacío." -ForegroundColor Yellow
    }
}

function Confirm-Step {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Prompt
    )

    $answer = Read-Host "$Prompt [S/n]"
    return [string]::IsNullOrWhiteSpace($answer) -or $answer -match "^(s|si|sí|y|yes)$"
}

function Write-Step {
    param(
        [Parameter(Mandatory = $true)]
        [int]$Number,
        [Parameter(Mandatory = $true)]
        [string]$Message
    )

    Write-Host ""
    Write-Host "[$Number] $Message" -ForegroundColor Cyan
}

if (-not (Get-Command kubectl -ErrorAction SilentlyContinue)) {
    throw "kubectl no está disponible en el PATH. Cierra y abre la terminal nuevamente."
}

$context = kubectl config current-context
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($context)) {
    throw "No hay un contexto Kubernetes activo."
}

Write-Host "Contexto actual: $context" -ForegroundColor Cyan
Write-Host "Este script despliega la infraestructura en pasos y no muestra las credenciales." -ForegroundColor DarkGray
if (-not (Confirm-Step "¿Continuar con este contexto?")) {
    Write-Host "Despliegue cancelado."
    exit 0
}

Write-Step 1 "Validando el clúster Kubernetes"
Invoke-Kubectl @("get", "nodes")

try {
    Write-Step 2 "Creando o verificando el namespace ecommerce"
    if (Confirm-Step "¿Crear o actualizar el namespace '$namespace'?") {
        Invoke-Kubectl @("apply", "-f", (Join-Path $k8sPath "namespace.yaml"))
    }

    Write-Step 3 "Aplicando la configuración general"
    if (Confirm-Step "¿Aplicar el ConfigMap base?") {
        Invoke-Kubectl @("apply", "-f", (Join-Path $k8sPath "configmap.yaml"))
    }

    Write-Step 4 "Preparando las credenciales"
    & kubectl get secret ecommerce-secrets -n $namespace -o name 2>$null | Out-Null
    $secretExists = $LASTEXITCODE -eq 0

    if ($secretExists) {
        $encodedDbUser = kubectl get secret ecommerce-secrets -n $namespace -o jsonpath='{.data.POSTGRES_USER}'
        $dbUser = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($encodedDbUser))
        Write-Host "El Secret ecommerce-secrets ya existe; se conservará." -ForegroundColor Yellow
        Write-Host "Se usará el usuario almacenado en el Secret para verificar PostgreSQL." -ForegroundColor DarkGray
    }
    else {
        $dbUser = Read-Host "Usuario de PostgreSQL [ecommerce]"
        if ([string]::IsNullOrWhiteSpace($dbUser)) {
            $dbUser = "ecommerce"
        }

        $dbPassword = Read-RequiredSecret "Password de PostgreSQL (no se mostrará)"
        $jwtSecret = Read-RequiredSecret "JWT secret de mínimo 32 caracteres (no se mostrará)"

        if ($jwtSecret.Length -lt 32) {
            throw "El JWT secret debe tener al menos 32 caracteres."
        }

        if (-not (Confirm-Step "¿Crear el Secret de Kubernetes?")) {
            throw "No se puede desplegar PostgreSQL sin ecommerce-secrets."
        }

        & kubectl create secret generic ecommerce-secrets `
            --namespace $namespace `
            --from-literal=POSTGRES_USER=$dbUser `
            --from-literal=POSTGRES_PASSWORD=$dbPassword `
            --from-literal=JWT_SECRET=$jwtSecret `
            --dry-run=client -o yaml | kubectl apply -f -

        if ($LASTEXITCODE -ne 0) {
            throw "No se pudo crear o actualizar ecommerce-secrets."
        }
    }

    Write-Step 5 "Desplegando PostgreSQL"
    if (-not (Confirm-Step "¿Desplegar PostgreSQL ahora?")) {
        Write-Host "Base de Kubernetes aplicada. PostgreSQL quedó pendiente."
        exit 0
    }

    Invoke-Kubectl @("apply", "-f", (Join-Path $k8sPath "postgres-init-configmap.yaml"))
    Invoke-Kubectl @("apply", "-f", (Join-Path $k8sPath "postgres-pvc.yaml"))
    Invoke-Kubectl @("apply", "-f", (Join-Path $k8sPath "postgres-service.yaml"))
    Invoke-Kubectl @("apply", "-f", (Join-Path $k8sPath "postgres-statefulset.yaml"))

    Write-Step 6 "Esperando y verificando PostgreSQL"
    Write-Host "Esperando a que PostgreSQL esté listo..." -ForegroundColor Cyan
    Invoke-Kubectl @("rollout", "status", "statefulset/postgres", "-n", $namespace, "--timeout=180s")
    Invoke-Kubectl @("get", "pods", "-n", $namespace)
    Invoke-Kubectl @("get", "pvc", "-n", $namespace)

    Write-Host "Bases de datos encontradas:" -ForegroundColor Cyan
    Invoke-Kubectl @(
        "exec", "postgres-0", "-n", $namespace, "--",
        "psql", "-U", $dbUser, "-d", "postgres", "-Atc",
        "SELECT datname FROM pg_database WHERE datname IN ('auth_db','catalogo_db','pedidos_db','pagos_db','notificaciones_db') ORDER BY datname"
    )

    Write-Host "Despliegue base completado." -ForegroundColor Green
}
finally {
    $dbPassword = $null
    $jwtSecret = $null
    [GC]::Collect()
}
