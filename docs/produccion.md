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
contra las imágenes publicadas. **No** cubre TLS, backups, HA ni SASL de Kafka.

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

**Hoy / estado:** el bootstrap de laboratorio (`Admin1234`) no corre en `prod`.
El Job `auth-admin-init` crea el primer ADMIN con `AUTH_ADMIN_EMAIL` /
`AUTH_ADMIN_PASSWORD` del Secret (no pisa un usuario que ya exista). Un ADMIN
viejo de demo hay que rotarlo a mano.

**Archivos:** `auth-service/.../AdminInitRunner.java`,
`k8s/auth-admin-init-job.yaml`, `scripts/deploy-k8s-full.ps1`, Secret.

### 3. Grafana

**Hoy:** anónimo desactivado en k8s y Compose. ClusterIP (no Ingress).
Credenciales `GRAFANA_ADMIN_USER` / `GRAFANA_ADMIN_PASSWORD` **obligatorias**
en el Secret (sin `optional: true`). `deploy-k8s-full.ps1` aplica Grafana.

**Hecho:**

- Anónimo desactivado; usuario y password desde `ecommerce-secrets`.
- **No** publicar Grafana (ni Prometheus) en el Ingress público: ClusterIP +
  port-forward, VPN o ingress interno.

**Archivos:** `k8s/grafana-deployment.yaml`, `docker-compose.yml`, Secret.

---

## Fase 2 — Red, TLS y superficie HTTP

### 4. CORS

**Hoy / estado:** en `prod` el gateway ya no usa `*`. Orígenes:
`http://localhost:*`, `http://127.0.0.1:*`, `https://ecommerce.local`.
Compose (profile por defecto) sigue con `*` para lab.

### 5. Actuator, Swagger y consola H2

**Hoy / estado:** en `prod`, H2 y Swagger apagados; JWT no deja públicos
`/swagger-ui` ni `/h2-console` (sí health y prometheus para probes/scrape).
`show-sql: false`. Laboratorio sin cambios.

### 6. Redis (rate limiting)

**Hoy / estado:** Redis con `--requirepass` en Kubernetes (`REDIS_PASSWORD` en
`ecommerce-secrets`). El gateway recibe `SPRING_DATA_REDIS_PASSWORD`. En
`prod`, `RedisFailClosedFilter` responde 503 si Redis no responde (el
`RedisRateLimiter` 4.1 sigue siendo fail-open internamente). Compose de lab
sigue opcionalmente sin password; el puerto se publica solo en `127.0.0.1`.
Rutas autenticadas limitan por `sub` JWT; login/register y peticiones sin
token válido siguen por IP.

**Pendiente:** Tras añadir `REDIS_PASSWORD` al Secret vivo, volver a sellar
(`scripts/seal-ecommerce-secrets.ps1`) para que el SealedSecret no pise la clave.

**Archivos:** `docker-compose.yml`, `k8s/redis-deployment.yaml`,
`k8s/api-gateway-deployment.yaml`, `api-gateway` (`RedisFailClosedFilter`,
`application-prod.yml`), `scripts/deploy-k8s-full.ps1`, Secret.

### 7. Kafka

**Hoy / estado:** Kafka es ClusterIP. El Job declara los tópicos de contrato
(ADR-0018). En Kubernetes `KAFKA_CFG_AUTO_CREATE_TOPICS_ENABLE=false`. Compose
de lab sigue con auto-create `true`. Broker aún PLAINTEXT.

**Pendiente:** SASL/SCRAM o mTLS.

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

**Hoy / estado:** Flyway + `ddl-auto: validate` en los cinco servicios
(ADR-0017). `baseline-on-migrate` cubre PVC brownfield. Compose/H2 de tests
siguen el mismo V1.

**Pendiente:** no reintroducir `ddl-auto: update`; cada cambio de entidad lleva
script `V{n}__…`. Backups aparte (punto 11).

### 11. Copias de seguridad

**Hoy / estado:** CronJob `postgres-backup` (03:00 UTC) hace `pg_dump` custom
de las cinco bases a un PVC (`postgres-backup-pvc`), retención 14 días.
Restore: `pg_restore` desde un dump del PVC (ensayar a mano). Sigue habiendo
un solo Postgres (SPOF). Object storage (S3) queda pendiente.

### 12. Imágenes y cadena de suministro

**Hoy / estado:** el deploy usa tag git (`vX.Y.Z`). El workflow de release
publica solo ese tag (no `latest`). Trivy (CRITICAL, unfixed ignorados) corre
tras el push. Dependabot semanal (Maven y Actions). Restore de `pg_dump`
ensayado contra el PVC (`catalogo_restore_drill`).

**Pendiente:** object storage (S3).

---

## Fase 4 — Resiliencia (después del go-live interno)

No bloquean un primer entorno interno **ya endurecido** en las fases 1–3.
Sí bloquean un SLA serio.

| Mejora | Por qué |
|---|---|
| Timeouts explícitos en `RestClient` (catálogo, auth, pedidos) | Hecho: 2s connect / 5s read (`HTTP_CONNECT_TIMEOUT`, `HTTP_READ_TIMEOUT`) |
| Circuit breaker (Resilience4j) en clientes HTTP | Aislar fallos de catálogo/pedidos |
| Rate limit por `sub` JWT además de IP | Hecho: `JwtOrIpKeyResolver`; login sigue por IP |
| Sampling Zipkin `0.05`–`0.1`; store persistente (o Tempo) | Sampling k8s `0.1`; Zipkin en memoria se pierde (ADR-0011) |
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
| [0004](./adr/ADR-0004-persistencia-por-servicio.md) | Un Postgres para todos; Flyway cubre las cinco bases (ADR-0017) |
| [0003](./adr/ADR-0003-kafka-asiincrono.md) / [0018](./adr/ADR-0018-topicos-kafka-declarados.md) | Auto-create false en k8s; Job de tópicos; SASL pendiente |
| [0007](./adr/ADR-0007-exposicion-puertos-internos.md) | NetworkPolicies no efectivas en kindnet |
| [0008](./adr/ADR-0008-https-ingress.md) | TLS self-signed; cert-manager pendiente |
| [0009](./adr/ADR-0009-secretos-sealed.md) | Vault/ESO pendiente; Sealed Secrets acoplado a la clave del cluster |
| [0010](./adr/ADR-0010-rate-limiting-redis.md) | JWT `sub` + IP; RedisRateLimiter nativo sigue fail-open (mitigado en prod) |
| [0011](./adr/ADR-0011-observabilidad.md) | Sampling k8s 0.1; Zipkin sin persistencia; sin trace en Kafka |
| [0013](./adr/ADR-0013-compensacion-stock-saga-outbox.md) / [0019](./adr/ADR-0019-outbox-ack-kafka.md) | Compensación eventual; DLT sin reproceso automático; ack de produce cubierto |
| [0015](./adr/ADR-0015-arranque-ordenado-gateway-auth.md) | Orden Compose ≠ orden real en k8s (probes) |
