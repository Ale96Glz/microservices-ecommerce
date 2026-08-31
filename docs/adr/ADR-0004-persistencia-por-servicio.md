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

El esquema se genera con `spring.jpa.hibernate.ddl-auto: update` (Hibernate).
**No se usa Flyway/Liquibase** (solo existe una carpeta `db/migration` vacía).

Entidades por dominio:

| Servicio | Base | Entidades |
|---|---|---|
| `auth-service` | `auth_db` | `usuario` |
| `catalogo-service` | `catalogo_db` | `categoria`, `producto` |
| `pedidos-service` | `pedidos_db` | `pedido`, `pedido_item` |
| `pagos-service` | `pagos_db` | `pago` (UNIQUE `pedido_id`) |
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

- **Sin migraciones versionadas**: `ddl-auto: update` no deja historial de
  esquema ni rollback; migraciones destructivas o de producción son arriesgadas
  (deuda técnica a resolver con Flyway/Liquibase, `Fase 4`).
- **H2 vs PostgreSQL**: aunque el modo `MODE=PostgreSQL` mitiga diferencias,
  persisten divergencias (funciones, tipos, índices) no cubiertas.
- **Un solo servidor Postgres** compartido entre 5 bases (docker-compose) es un
  punto único de fallo; requiere réplica/ha en producción.
- **Duplicación de drivers/configuración** en cada `pom.xml` y `application.yml`.
