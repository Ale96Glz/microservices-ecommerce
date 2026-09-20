# Camino a producción

Estado de madurez del stack (gateway, auth, catálogo, pedidos, pagos,
notificaciones, Compose y Kubernetes) y plan para endurecerlo antes de
exponerlo a internet con datos reales.

- **Fecha de la revisión:** 2026-09-19
- **Veredicto:** **no está listo para producción**
- **Alcance:** revisión estática del repositorio. No incluye pentest, load
  test ni auditoría del cluster destino.
- **Relacionado:** [escenarios de compra](./escenarios-de-compra.md),
  [ADR](./adr/README.md), smoke E2E (`.github/workflows/smoke.yml`,
  `scripts/smoke-test.sh`).

## Criterio

“Listo para producción” aquí significa: el stack puede recibir tráfico real
(usuarios, pedidos, pagos simulados persistidos) sin secretos por defecto,
sin superficie HTTP innecesaria, con schema versionado, copias de seguridad y
red/TLS acordes a un cluster de verdad.

Un **smoke E2E en verde** confirma el camino feliz y la saga de compensación
contra las imágenes publicadas. **No** cubre secretos, TLS, backups, HA,
fail-closed de Redis ni el cierre de Actuator/Swagger.

## Lo que ya se puede conservar

| Área | Qué hay |
|---|---|
| Dominio | Outbox transaccional, saga de restock (ADR-0013), intentos de pago (ADR-0016), DLT Kafka |
| Identidad | BCrypt, el gateway reescribe `X-User-*`, cada servicio revalida JWT (ADR-0006), `requireSelfOrAdmin` |
| Kubernetes | Probes startup/ready/live, resource limits, SealedSecret (ADR-0009), Ingress hacia el gateway |
| CI | `mvn verify`, release a GHCR, smoke E2E contra imágenes publicadas (ADR-0012) |
| Contratos | ADRs y escenarios de compra alineados con el smoke |
| Observabilidad (lab) | Logs JSON, Prometheus, Zipkin, Grafana (ADR-0011) — útiles en local, no operativos en prod |

## Principio de endurecimiento

Introducir un profile `prod` (o ConfigMap + variables de entorno **sin**
defaults peligrosos). Lo aceptable en lab **no debe arrancar** en producción
si falta un secreto, si Hibernate puede mutar el schema o si Swagger/H2
quedan abiertos.

Orden recomendado:

1. Identidad y secretos
2. Red, TLS y superficie HTTP
3. Datos y supply chain
4. Resiliencia (después de un entorno interno endurecido)

Un Postgres en réplica 1 es aceptable para un primer entorno pequeño **si**
hay backup y restore ensayado. Arrancar con `Admin1234` y JWT `change-me` no
lo es.

---

## Fase 1 — Identidad y secretos

### 1. JWT y password de PostgreSQL

**Hoy:** `jwt.secret` cae a `change-me-to-a-long-random-secret-key-at-least-32-chars`
en los `application.yml` y en Compose. Postgres usa `ecommerce` / `ecommerce`
(`.env.example`, `docker-compose.yml`). Un JWT comprometido firma tokens para
**todos** los servicios (ADR-0002).

**Estado:** implementado el fail-fast en profile `prod` (`ProductionSecrets` en
`common-security`, `application-prod.yml`, `SPRING_PROFILES_ACTIVE=prod` en
k8s). Compose y tests locales siguen en profile por defecto (lab).

**Cambio (hecho / operación):**

- En producción, **sin fallback**: si `JWT_SECRET` tiene menos de 32 caracteres
  o `POSTGRES_PASSWORD` está vacío, el proceso sale al arrancar (fail-fast).
- Rotar el `SealedSecret` con `scripts/seal-ecommerce-secrets.ps1` (ADR-0009).
- Compose solo lee un `.env` local, nunca versionado. Ampliar
  `secret.example.yaml` si Grafana/Redis/Kafka ganan secretos.

**Archivos:** `*/src/main/resources/application.yml`, `docker-compose.yml`,
`.env.example`, `k8s/sealed-ecommerce-secrets.yaml`, `secret.example.yaml`.

### 2. Usuario ADMIN de demo

**Hoy:** `AdminBootstrap` crea `admin@ecommerce.local` / `Admin1234` si no
existe y lo escribe en logs. El README publica esas credenciales. El smoke
promueve un usuario vía `psql` (no depende de este bootstrap).

**Cambio:**

- `@Profile("!prod")` o `AUTH_BOOTSTRAP_ADMIN=false` en producción.
- El primer ADMIN sale de un Job/script one-shot que lee el password del
  Secret, no de código compilado.
- No loguear contraseñas en claro.

**Archivos:** `auth-service/.../AdminBootstrap.java`, README (credenciales
solo como lab).

### 3. Grafana

**Hoy:** `GF_AUTH_ANONYMOUS_ENABLED=true`, rol `Admin`, usuario/password
`admin` en `k8s/grafana-deployment.yaml` (y anónimo en Compose).

**Cambio:**

- Anónimo desactivado; usuario y password desde `ecommerce-secrets`.
- **No** publicar Grafana (ni Prometheus) en el Ingress público: ClusterIP +
  port-forward, VPN o ingress interno.

**Archivos:** `k8s/grafana-deployment.yaml`, `docker-compose.yml`, Secret.

---

## Fase 2 — Red, TLS y superficie HTTP

### 4. CORS

**Hoy:** en `api-gateway` `allowedOriginPatterns: "*"` con
`allowCredentials: true`. Cualquier origen puede usar credenciales del
navegador.

**Cambio:** lista explícita `GATEWAY_CORS_ORIGINS=https://app.tudominio`. No
usar `*` si hay cookies/credenciales.

**Archivos:** `api-gateway/src/main/resources/application.yml`,
`k8s/configmap.yaml`.

### 5. Actuator, Swagger y consola H2

**Hoy:** `JwtAuthFilter` marca como públicos `/actuator/prometheus`,
`/swagger-ui`, `/v3/api-docs`, `/h2-console`. Los servicios exponen
`health,info,prometheus` y `h2-console.enabled: true`.

**Cambio en prod:**

- H2 y Swagger desactivados.
- Actuator: solo `health` / `liveness` / `readiness` hacia el cluster.
- Prometheus scrape por ClusterIP (red interna), no por Ingress.
- `show-sql: false`.

**Archivos:** `common-security/.../JwtAuthFilter.java`, `application.yml` de
cada servicio, profile `prod`.

### 6. Redis (rate limiting)

**Hoy:** Redis sin password, puerto `6379` publicado en Compose, rate limit
por IP (`GatewayRateLimitConfig`), política **fail-open** documentada para
dev (ADR-0010).

**Cambio:**

- `--requirepass` + `SPRING_DATA_REDIS_PASSWORD` en el Secret.
- No publicar `6379` al host en ningún entorno que no sea lab local.
- En prod: **fail-closed** (si Redis cae → 503, no tráfico libre).
- Opcional: KeyResolver por `sub` del JWT además de IP (NAT comparte IP).

**Archivos:** `docker-compose.yml`, `k8s/redis-deployment.yaml`,
`api-gateway` (filtro y `application.yml`), Secret.

### 7. Kafka

**Hoy:** broker PLAINTEXT, `KAFKA_CFG_AUTO_CREATE_TOPICS_ENABLE: true`.

**Cambio:**

- Solo ClusterIP (sin NodePort/host en prod).
- SASL/SCRAM o mTLS.
- `auto-create` en `false`; tópicos declarados (`order-created`,
  `payment-processed`, `restock-requested`, DLT, etc.).

**Archivos:** `docker-compose.yml`, `k8s/kafka-statefulset.yaml`, configs
Spring Kafka de pedidos/pagos/notificaciones/catálogo.

### 8. TLS del Ingress

**Hoy:** secret `ecommerce-tls`, host `ecommerce.local`, certificado
autofirmado (`scripts/gen-tls.ps1`, ADR-0008). Compose es HTTP `:8080`.

**Cambio:** cert-manager + Let's Encrypt, host público, redirección
HTTP→HTTPS. Dejar de usar la CA propia como modelo de prod. El ADR-0009
mantiene el TLS de Ingress fuera de Sealed Secrets a propósito; cert-manager
gestiona el Secret TLS.

**Archivos:** `k8s/ingress.yaml`, manifiestos de `ClusterIssuer`/`Certificate`.

### 9. NetworkPolicies

**Hoy:** `k8s/kustomization.yaml` no incluye políticas de red. Kind/kindnet
no las aplica (ADR-0007). ClusterIP no sustituye un default-deny.

**Cambio:**

- Default-deny en el namespace `ecommerce`.
- Permitir: Ingress → gateway; gateway → microservicios; servicios →
  postgres / kafka / redis por label; Prometheus → `/actuator/prometheus`.
- CNI que cumpla políticas (Calico, Cilium, etc.).

**Archivos:** nuevos `k8s/*-networkpolicy.yaml` registrados en
`kustomization.yaml`.

---

## Fase 3 — Datos y supply chain

### 10. Migraciones de schema

**Hoy (pagos):** Flyway (`db/migration`) + `ddl-auto: validate` (ADR-0017).
El resto de servicios sigue en `update` hasta su PR.

**Cambio (resto de servicios):**

- Flyway **por servicio**, `classpath:db/migration`.
- Baseline del schema actual; `prod` con `validate`.

**Archivos:** `application.yml` de cada servicio, `db/migration`, eliminar o
acotar `PagoLegacyUniqueConstraintMigration`.

### 11. Copias de seguridad

**Hoy:** un Postgres, cinco bases (`auth_db`, `catalogo_db`, `pedidos_db`,
`pagos_db`, `notificaciones_db`), PVC, sin CronJob ni restore documentado.
Un solo Postgres es SPOF (ADR-0004).

**Cambio:**

- CronJob `pg_dump` de las cinco bases hacia object storage (S3, MinIO, etc.).
- Retención explícita.
- Drill de restore (al menos trimestral) documentado aquí o en runbooks.

**Archivos:** nuevo manifiesto CronJob + runbook de restore. `infra/init-dbs.sql`
sigue siendo el mapa de bases.

### 12. Imágenes y cadena de suministro

**Hoy:** `${IMAGE_VERSION:-latest}` / `${IMAGE_TAG:-latest}`. El workflow de
release también tagea `latest`. No hay Trivy/Grype ni CodeQL/Dependabot.

**Cambio:**

- Desplegar solo tag inmutable (`vX.Y.Z` o SHA de git), nunca `latest` en prod.
- En `.github/workflows/release-images.yml`: escaneo (Trivy o equivalente) y
  fallar el job ante vulnerabilidades CRITICAL.
- Dependabot o renovate para Maven y acciones de GitHub.

**Archivos:** `k8s/*-deployment.yaml`, `docker-compose.yml`,
`.github/workflows/release-images.yml`.

---

## Fase 4 — Resiliencia (después del go-live interno)

No bloquean un primer entorno interno **ya endurecido** en las fases 1–3.
Sí bloquean un SLA serio.

| Mejora | Por qué |
|---|---|
| Timeouts explícitos en `RestClient` (catálogo, auth, pedidos) | Hoy un servicio colgado puede agotar hilos |
| Circuit breaker (Resilience4j) en clientes HTTP | Aislar fallos de catálogo/pedidos |
| Rate limit por `sub` JWT además de IP | NAT comparte IP; el burst de login es pequeño |
| Sampling Zipkin `0.05`–`0.1`; store persistente (o Tempo) | Sampling `1.0` satura; Zipkin en memoria se pierde (ADR-0011) |
| Alertmanager (error rate, lag Kafka, disco PVC) | Prometheus sin alertas no opera |
| Réplicas de gateway/auth/catálogo | Todo k8s está en `replicas: 1` |
| HA de Postgres / Kafka | SPOF de datos; no hace falta el día 1 si hay backup |
| TraceId en mensajes Kafka | La cadena async no se une en Zipkin (ADR-0011) |
| Reproceso de DLT | Los listeners DLT solo registran log (ADR-0013) |

---

## Archivos a tocar primero (fase 1)

- `*/src/main/resources/application.yml`
- `common-security/.../JwtAuthFilter.java`
- `auth-service/.../AdminBootstrap.java`
- `api-gateway/src/main/resources/application.yml`
- `k8s/configmap.yaml`
- `k8s/grafana-deployment.yaml`
- `secret.example.yaml`
- `.github/workflows/release-images.yml` (fase 3)

## Deuda ya reconocida en ADRs

Varios bloqueadores están escritos como consecuencia negativa de una
decisión, no como olvido:

| ADR | Deuda viva para prod |
|---|---|
| [0002](./adr/ADR-0002-gateway-jwt-centralizado.md) | Un `JWT_SECRET` compromete todo; rotación no automatizada |
| [0004](./adr/ADR-0004-persistencia-por-servicio.md) | Un Postgres para todos; Flyway solo en pagos (ADR-0017) |
| [0003](./adr/ADR-0003-kafka-asiincrono.md) / [0018](./adr/ADR-0018-topicos-kafka-declarados.md) | Auto-create aún true en lab; Job de tópicos cubre el contrato |
| [0007](./adr/ADR-0007-exposicion-puertos-internos.md) | NetworkPolicies no efectivas en kindnet |
| [0008](./adr/ADR-0008-https-ingress.md) | TLS self-signed; cert-manager pendiente |
| [0009](./adr/ADR-0009-secretos-sealed.md) | Vault/ESO pendiente; Sealed Secrets acoplado a la clave del cluster |
| [0010](./adr/ADR-0010-rate-limiting-redis.md) | Redis sin password; fail-open; rate limit por IP |
| [0011](./adr/ADR-0011-observabilidad.md) | Sampling 1.0; Zipkin sin persistencia; sin trace en Kafka |
| [0013](./adr/ADR-0013-compensacion-stock-saga-outbox.md) | Compensación eventual; DLT sin reproceso automático |
| [0015](./adr/ADR-0015-arranque-ordenado-gateway-auth.md) | Orden Compose ≠ orden real en k8s (probes) |
