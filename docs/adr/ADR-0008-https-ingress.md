# ADR-0008: HTTPS con TLS por defecto mediante Ingress

- **Estatus:** Aceptado
- **Fecha:** 2026-09

## Contexto y Problema

El único punto de entrada externo es el Ingress hacia `api-gateway:8080`
(ADR-0005 y ADR-0007). Hoy el tráfico externo viaja **en claro (HTTP)**: el
Ingress no define `spec.tls` ni `host`, y el cluster es un entorno de desarrollo
local (Kind/Docker Desktop vía WSL2) **sin dominio público** ni IP paplicada.

Se necesita terminar TLS en el edge para que cualquier consumidor (frontend,
scripts, pruebas) use HTTPS por defecto, manteniendo la operabilidad del
desarrollo local.

## Opciones Evaluadas

1. **Let's Encrypt + cert-manager** (`ClusterIssuer` con retos HTTP-01 o
   TLS-ALPN): emite certificados públicos sin costo y con renovación
   automática, pero **requiere un dominio público alcanzable desde Internet** y
   puertos 80/443 accesibles desde fuera. En el cluster local (IP privada,
   sin dominio) no es aplicable hoy.
2. **MKCERT (CA local en la máquina)**: emite certificados de prueba con una CA
   raíz local instalada en el navegador/máquina. Muy cómoda para un solo
   desarrollador, pero la CA es **por máquina** y no es reproducible para
   colaboradores ni para el repositorio.
3. **Certificado autofirmado con CA propia (openssl + Secret TLS)**: un script
   crea una CA de revisión y emite un certificado para el hostname del Ingress;
   el certificado se guarda como Secret `tls` en Kubernetes y el Ingress lo
   termina. La CA se confía manualmente en los clientes de desarrollo. Es
   **reproducible desde el repo** y no depende de dominio público.
4. **Sin HTTPS**: mantener HTTP. Descartado porque la Agenda Fase 3 exige
   HTTPS y porque buena parte del frontend/entorno asumirá `https://`.

## Decisión

1. **TLS por defecto con self-signed CA propia** (opción 3), delimitada al
   entorno local:
   - Un script (`scripts/gen-tls.ps1` o `.sh`) genera la CA de desarrollo
     (`ca-ecommerce`) y un certificado para `ecommerce.local`.
   - El certificado/CA se guarda en el cluster como Secret TLS
     `ecommerce-tls` (namespace `ecommerce`) mediante `kubectl create secret tls`.
   - El Ingress `api-gateway` agrega:
     - `host: ecommerce.local` en la regla.
     - `spec.tls[].hosts: [ecommerce.local]` + `secretName: ecommerce-tls`.
   - El certificado **no se versiona en git** (solo el script y un `.gitignore`
     para `certs/`).
2. **Mantener HTTP en paralelo** en el Ingress (sin redirección forzosa a
   HTTPS) durante el desarrollo: los `port-forward` y tests actuales siguen
   funcionando. La redirección `http→https` se decide en el despliegue, no en
   este ADR.
3. **Consumo local**: agregar `127.0.0.1 ecommerce.local` al archivo de
   hosts del sistema de desarrollo y confiar en la CA raíz de desarrollo en el
   navegador/herramientas. Documentado en el README.
4. **Let's Encrypt/cert-manager queda como deuda documentada** para un entorno
   con dominio público (producción/`staging` con DNS real). Al momento de
   usarlo se reemplaza la opción 1 manteniendo el mismo `host` y estructura TLS.

## Consecuencias

### Positivas

- **HTTPS desde el edge por defecto**: el Ingress termina TLS y el servicio
  interno sigue en HTTP dentro del cluster (sin reconfigurar los servicios).
- **Reproducible en el repo**: un script genera CA+cert y aplica el Secret;
  cualquier colaborador lo obtiene igual.
- **Sin bloqueos de red**: no depende de dominio público ni de puertos
  accesibles desde Internet; funciona en el cluster local.
- **Transición acotada a producción**: al haber dominio, se intercambia la CA
  por cert-manager sin cambiar el Ingress.

### Negativas / Compromisos

- **Advertencias de certificado**: una CA de desarrollo no está en los
  trust stores por defecto; hay que confiarla manualmente (documentado).
- **Mantenimiento local**: los certificados self-signed caducan y hay que
  regenerarlos/replicar el Secret (script disponible).
- **HTTPS solo en el edge**: el tráfico interno entre servicios sigue en HTTP
  (aceptado; es la práctica habitual con mTLS como mejora futura).
- **HTTP paralelo**: durante dev el Ingress acepta ambos protocolos; no hay
  obligación de HTTPS único hasta definir el despliegue final.