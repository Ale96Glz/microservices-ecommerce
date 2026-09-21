# ADR-0018: Tópicos Kafka declarados antes de los consumidores

- **Estatus:** Aceptado
- **Fecha:** 2026-09

## Contexto y Problema

El ADR-0003 adoptó Kafka con `KAFKA_CFG_AUTO_CREATE_TOPICS_ENABLE=true`. El
productor crea el tópico en el primer produce. Si el consumidor arrancó
antes (catálogo escuchando `restock-requested`), el broker aún no tiene el
tópico: el listener no se suscribe, el outbox de pedidos marca el evento
`PUBLICADO` y **el restock no ocurre** (stock queda reservado tras un
`PAGO_RECHAZADO`). Se observó en k8s con warning
`UNKNOWN_TOPIC_OR_PARTITION` y tópico creado segundos después, tarde para
ese mensaje.

El auto-create tampoco garantiza particiones, factor de replicación ni DLT.

## Opciones Evaluadas

1. **Job/init que crea los tópicos conocidos antes de los microservicios
   (elegida)**: lista cerrada alineada con `KafkaTopics` + `.DLT`. Compose:
   servicio `kafka-init-topics` tras Kafka healthy. Kubernetes: Job tras el
   StatefulSet Ready, **antes** de aplicar deployments de app.
2. **AdminClient en cada Spring Boot** (`NewTopic` beans): duplica la lista
   en cinco servicios; el primero que arranca gana — carrera si Kafka no
   está listo.
3. **Dejar auto-create**: barato y ya falló en la saga de compensación.
4. **Desactivar auto-create sin bootstrap**: el primer produce falla; peor.

## Decisión

1. **Tópicos de contrato** (1 partición, RF=1 en lab; RF se sube con un
   cluster real):

   | Tópico | Uso |
   |---|---|
   | `order-created` | pedidos → pagos, notificaciones |
   | `order-created.DLT` | fallos de consumo (ADR-0013) |
   | `payment-processed` | pagos → pedidos, notificaciones |
   | `payment-processed.DLT` | |
   | `restock-requested` | pedidos → catálogo (compensación) |
   | `restock-requested.DLT` | |

2. **Bootstrap:** `infra/kafka-create-topics.sh` (idempotente `--if-not-exists`),
   invocado por Compose y por el Job `k8s/kafka-topics-job.yaml`.
   `deploy-k8s-full.ps1` espera el Job `Complete` antes de los microservicios.
3. **Auto-create** es `false` en Kubernetes (el Job de tópicos es el gate).
   En Compose de lab permanece `true` como red de seguridad para tópicos no
   listados.
4. **Ack de produce:** el outbox solo pasa a `PUBLICADO` cuando
   `KafkaTemplate.send(...).get(...)` confirma (ADR-0019). Un `UNKNOWN_TOPIC`
   deja la fila `PENDIENTE` y se reintenta. **Replay** de un outbox ya
   `PUBLICADO` (mensaje perdido tras un ack) sigue siendo operación (reset a
   `PENDIENTE` o DLT).

## Consecuencias

### Positivas

- Los consumidores encuentran el tópico al suscribirse.
- La lista vive en un solo script, no en cinco `application.yml`.
- El smoke/k8s deja de depender de una carrera Kafka.

### Negativas / Compromisos

- Un tópico nuevo exige cambiar el script **y** el código (`KafkaTopics`).
- RF=1 no es HA (mismo SPOF del ADR-0003).
- Auto-create `true` en Compose puede ocultar un olvido en el script hasta
  Kubernetes (`false` + Job).
- El Job de k8s hay que borrarlo/recrearlo para reejecutarlo (Jobs inmutables).
