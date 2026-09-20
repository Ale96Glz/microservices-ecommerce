# ADR-0017: Migraciones versionadas con Flyway (primero pagos-service)

- **Estatus:** Aceptado
- **Fecha:** 2026-09

## Contexto y Problema

El ADR-0004 dejó el esquema en manos de `spring.jpa.hibernate.ddl-auto: update`.
En un cluster con PVC persistente (Postgres de semanas), subir una imagen que
añade columnas (p. ej. `pago.intento` del ADR-0016) **no altera** la tabla
existente. Hibernate da por bueno el schema; la app consulta `intento` y Postgres
responde `column does not exist` (HTTP 500 en `GET /pago/pedido/{id}`, el
listener de `order-created` no puede persistir).

El parche operativo (drop de `pago` + restart) recupera un lab. No escala a
producción ni a cinco servicios.

`PagoLegacyUniqueConstraintMigration` (JdbcTemplate al arranque) es otra
migración oculta, no versionada y solo PostgreSQL.

## Opciones Evaluadas

1. **Flyway por servicio (elegida)**: scripts `db/migration` en el classpath;
   Spring Boot los aplica **antes** de Hibernate. En `prod`, `ddl-auto: validate`
   (o `none`): si el código y la BD no coinciden, el pod no arranca.
2. **Liquibase**: equivalente; el equipo ya usa SQL simple y Spring Boot trae
   Flyway de serie — menos XML/YAML.
3. **Seguir con `ddl-auto: update` + ALTER manual**: lo que falló en k8s 1.1.1.
4. **Borrar PVC en cada release**: inaceptable fuera de un lab.

## Decisión

1. **Flyway es la fuente de verdad del schema.** Hibernate deja de crear
   columnas en caliente.
2. **Piloto: `pagos-service`** (el que ya rompió en 1.1.1). El resto de
   servicios se migran igual, un módulo por PR, sin un big-bang.
3. **Convención de scripts:**
   - `V1__baseline_*.sql`: CREATE TABLE IF NOT EXISTS del modelo actual
     (instalación vacía: H2 de tests, Postgres nuevo).
   - `V2__…`: ALTER idempotentes (`ADD COLUMN IF NOT EXISTS`) para PVC
     brownfield. `baseline-on-migrate=true`: una BD ya poblada **sin** historial
     Flyway se marca como versión 1 y solo corre V2+.
4. **Profile `prod`:** `spring.jpa.hibernate.ddl-auto=validate` y Flyway
   obligatorio. Lab/H2 de `@DataJpaTest` sigue con `create-drop` (Flyway off).
5. **No se adopta** un único Flyway para las cinco bases: cada servicio posee
   su `pagos_db` / `pedidos_db` (ADR-0004).

Esta decisión **complementa** el ADR-0004 (sigue habiendo una BD por servicio;
cambia *cómo* evoluciona el esquema). **No** sustituye backups (fase 3.11).

## Consecuencias

### Positivas

- El schema viaja con la imagen; un tag `1.1.x` no depende de que Hibernate
  “adivine” ALTER.
- Historial en git (`flyway_schema_history`) y fallo explícito al arrancar.
- El drop manual de tablas deja de ser el procedimiento de release.

### Negativas / Compromisos

- Hay que escribir SQL a la par que las entidades; un olvido lo caza `validate`.
- Brownfield: `baseline-on-migrate` exige que V1 sea el estado *antes* de los
  ALTER de V2, o que V2 sea idempotente (elegido).
- Los otros cuatro servicios siguen en `ddl-auto: update` hasta su PR.
- H2 `MODE=PostgreSQL` no cubre todo el SQL de Postgres (ADR-0004).
