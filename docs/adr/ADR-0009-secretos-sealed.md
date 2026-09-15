# ADR-0009: Gestión segura de secretos con Kubernetes Sealed Secrets

- **Estatus:** Aceptado
- **Fecha:** 2026-09

## Contexto y Problema

Los secretos de la aplicación (`POSTGRES_USER`, `POSTGRES_PASSWORD`,
`JWT_SECRET`) viven en el Secret `ecommerce-secrets` del namespace `ecommerce`.
Hoy ese Secret **no se versiona en git** (correcto: no debe ir en claro al
repositorio) y se crea manualmente con `scripts/deploy-k8s.ps1`, que pide las
credenciales por consola.

Ese enfoque tiene dos carencias:

1. **No es reproducible desde el repo**: reconstruir el entorno (otro
   desarrollador, CI, una máquina nueva) requiere volver a teclear o transferir
   las credenciales, y no queda constancia de su estructura.
2. **No hay flujo GitOps**: `kubectl apply -k k8s/` no puede crear los secretos;
   se depende de pasos manuales fuera de los manifiestos.

Se necesita mantener los secretos fuera de git en claro, pero permitir que su
definición versionada se aplique con el `kustomization` existente.

## Opciones Evaluadas

1. **Kubernetes Sealed Secrets (bitnami-labs)**: un controller en el cluster
   posee una clave privada; `kubeseal` cifra el Secret con la clave pública y
   el resultado (`SealedSecret`) **se versiona en git**. Al aplicarlo, el
   controller lo descifra y crea el Secret real. La clave privada nunca sale
   del cluster. Se integra con `kustomization` como un recurso más.
   - Requiere instalar el controller (`sealed-secrets-controller`) y tener
     `kubeseal` en la máquina del operador.
2. **External Secrets Operator + Vault/cloud store**: los secretos viven en un
   store externo (Vault, AWS Secrets Manager, etc.) y un operator los inyecta en
   el cluster. Es lo habitual con un proveedor cloud o un Vault dedicado, pero
   añade infraestructura y credenciales de acceso al store — sobre-ingeniería
   para el entorno local (Kind/Docker Desktop).
3. **SOPS (Mozilla) con age/GPG**: cifra archivos YAML con una clave; se
   versionan cifrados y se descifran en el deploy/CI. No necesita un controller
   en el cluster, pero requiere gestionar y distribuir la clave de descifrado a
   cada operador/CI, y rompe el flujo "apply lo que está en el repo" (hay que
   descifrar antes de aplicar).
4. **Mantener el enfoque actual**: crear el Secret manualmente y no versionarlo.
   Sigue siendo válido para una sola máquina, pero no resuelve las carencias 1 y 2.

## Decisión

1. **Adoptar Kubernetes Sealed Secrets** (opción 1) para el secreto de
   aplicación `ecommerce-secrets`:
   - El controller `sealed-secrets-controller` se instala en el namespace
     `kube-system` mediante el manifiesto oficial de bitnami-labs (misma versión
     del chart estable).
   - El secreto real se cifra con `kubeseal` y el `SealedSecret` resultante se
     versiona como `k8s/sealed-ecommerce-secrets.yaml`, registrado en
     `kustomization.yaml`.
   - `kubectl apply -k k8s/` crea el Secret real automáticamente; el script
     `deploy-k8s.ps1` deja de solicitar credenciales cuando el `SealedSecret`
     ya existe en el repo.
   - Para **rotar/crear** credenciales se usa `scripts/seal-ecommerce-secrets.ps1`
     (o el flujo documentado con `kubeseal`), que cifra con la clave pública del
     controller y actualiza el `SealedSecret` versionado.
2. **El Secret TLS `ecommerce-tls`** (ADR-0008) NO se sella: su gestión sigue
   siendo el script `gen-tls.ps1`, porque se regenera con CA propia y caduca
   periódicamente (no es un secreto de aplicación con ciclo GitOps).
3. **`secret.example.yaml`** se mantiene como referencia de estructura y
   documentación del flujo de sellado, no como secreto operativo.
4. **No se adopta Vault/External Secrets o SOPS en este entorno** (opciones 2/3);
   quedan como deuda documentada para cuando haya proveedor cloud (producción).

## Consecuencias

### Positivas

- **Secretos versionados y reproducibles**: el `SealedSecret` es seguro en git
  y se aplica con el mismo `kubectl apply -k k8s/` que el resto de recursos.
- **Clave privada protegida**: vive solo en el cluster; ni el repo ni el
  operador la conocen.
- **Flujo GitOps completo**: la definición del secreto sigue la misma ruta que
  los deployments, services e ingress.
- **Rotación simple**: se re-cifra con `kubeseal` y se commitea el nuevo
  `SealedSecret`.
- **Integración con el script existente**: `deploy-k8s.ps1` simplifica (no pide
  credenciales si ya está sellado).

### Negativas / Compromisos

- **Operador necesita `kubeseal`** (y acceso al cluster) para rotar secretos;
  no basta editar YAML.
- **Dependencia del controller**: el `SealedSecret` no se descifra si el
  controller no está deployado; hay que instalarlo al provisionar el entorno.
- **No funciona con la clave pública de otro cluster**: cada cluster tiene su
  par de claves; sellar para el cluster local no sirve para otro entorno.
- **Otros secretos (TLS) quedan fuera** del flujo sellado por decisión expresa.