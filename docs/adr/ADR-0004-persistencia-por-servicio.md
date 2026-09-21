# ADR-0004: Persistencia por servicio (H2 en local, PostgreSQL en Docker)

- **Estatus:** Aceptado
- **Fecha:** 2023-10 (iniciativa del proyecto)

## Contexto y Problema

Cada microservicio necesita persistir su estado. La decisión pendiente es el
motor de base de datos y si se comparte un único servidor entre servicios.

Riesgos de una base de datos única: acoplamiento de esquemas entre dominios,
contención por escritura en transacciones distribuidas implícitas, y dificultad
para escalar dominios de forma independiente.

Se evaluaron tres opciones:

1. **Una sola base de datos compartida** para todos los servicios: simple de
   operar, pero rompe el aislamiento de dominio típico de microservicios.
2. **Base de datos por servicio** en PostgreSQL: aislamiento claro, pero exige
   gestión de múltiples esquemas/máquinas.
3. **H2 en memoria (local) + PostgreSQL (Docker)**: desarrollo ligero con Zero
   dependencies y persistencia real en entornos de integración/producción.

## Decisión

Se adopta **una base de datos independiente por servicio**, con **dos perfiles de
ejecución**:

- **Local / IDE**: cada servicio usa **H2 en memoria** en **modo PostgreSQL**
  (`MODE=PostgreSQL`), de modo que el plano local sea equivalente a producción.
  Ejemplos: `jdbc:h2:mem:catalogo_db`, `jdbc:h2:mem:pedidos_db`, etc.
- **Docker / Kubernetes**: se usa **PostgreSQL 16** con una base por servicio,
  creada por `infra/init-dbs.sql` (`auth_db`, `catalogo_db`, `pedidos_db`,
  `pagos_db`, `notificaciones_db`), sobrescribiendo la URL de datasource mediante
  variables de entorno `SPRING_DATASOURCE_*`.

El esquema lo versiona **Flyway** (ADR-0017) en los cinco servicios.
Hibernate usa `ddl-auto: validate` (no crea columnas en caliente).

Entidades por dominio:

| Servicio | Base | Entidades |
|---|---|---|
| `auth-service` | `auth_db` | `usuario` |
| `catalogo-service` | `catalogo_db` | `categoria`, `producto`, `restock_event` |
| `pedidos-service` | `pedidos_db` | `pedido`, `pedido_item`, `outbox_event` |
| `pagos-service` | `pagos_db` | `pago`, `outbox_event`, `configuracion` |
| `notificaciones-service` | `notificaciones_db` | `notificacion` |

## Consecuencias

### Positivas

- **Aislamiento de dominio**: cada servicio gestiona su esquema sin acuerdos
  entre equipos; un cambio en `pedido` no rompe `catalogo`.
- **Paridad local/producción**: el modo H2 PostgreSQL reduce sorpresas al
  portar a Postgres.
- **Desarrollo rápido**: H2 en memoria sin dependencias de infraestructura.
- **Idempotencia de pago** garantizada por `UNIQUE(pedido_id)` dentro del servicio
  de pagos (sin necesidad de transacción distribuida).

### Negativas / Compromisos

- **Schema versionado (ADR-0017)**: Flyway por servicio; Hibernate solo
  valida. Un olvido de script lo caza el arranque (`validate`).
- **H2 vs PostgreSQL**: aunque el modo `MODE=PostgreSQL` mitiga diferencias,
  persisten divergencias (funciones, tipos, índices) no cubiertas.
- **Un solo servidor Postgres** compartido entre 5 bases (docker-compose) es un
  punto único de fallo; requiere réplica/ha en producción.
- **Duplicación de drivers/configuración** en cada `pom.xml` y `application.yml`.
