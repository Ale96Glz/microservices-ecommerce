# ADR-0011: Observabilidad (logs estructurados, métricas y trazabilidad distribuida)

- **Estatus:** Aceptado
- **Fecha:** 2026-09

## Contexto y Problema

El sistema son microservicios Spring Boot (Boot 3.3.5, Cloud 2023.0.3) con
eventos asíncronos por Kafka (ADR-0003) y un API Gateway que enruta hacia los
servicios. Hoy solo se exponen health/readiness/liveness (actuator
`management.endpoints.web.exposure.include: health,info`), sin:

- **Logs estructurados**: los logs son texto plano en consola (UTF-8), difíciles
  de filtrar/parsear por herramienta agregadora.
- **Métricas**: no hay endpoint Prometheus; no se pueden observar tasas de
  request, latencias, JVM ni Kafka desde un dashboard.
- **Trazabilidad distribuida**: un pedido atraviesa gateway → pedidos →
  (Kafka) → pagos → notificaciones. Sin `traceId` compartido es imposible
  correlacionar esa cadena entre servicios y en los logs.

La Agenda Fase 3 exige incorporar los tres. Se necesita:
- Formato de log homogéneo en todos los servicios.
- Exportación de métricas estándar (`/actuator/prometheus`).
- Trazabilidad con un `traceId`/`spanId` que cruce gateway y eventos Kafka, y
  logs que lo incluyan en formato estructurado.
- Mínima duplicación: un módulo común (patrón ADR-0006/0009) en vez de repetir
  dependencias y config en cada servicio.

## Opciones Evaluadas

1. **Módulo común `common-observability` + stack Prometheus/Grafana/Zipkin**:
   - **Métricas**: `micrometer-registry-prometheus` + exposición
     `/actuator/prometheus`. Prometheus scrapea los servicios y Grafana
     grafica.
   - **Trazabilidad**: `micrometer-tracing-bridge-brave` +
     `zipkin-reporter-brave` (Brave es el puente moderno; **Sleuth quedó
     deprecado/retirado**, no aplica a Boot 3.3). Zipkin recibe los spans.
   - **Logs estructurados**: soporte **nativo de Spring Boot 3.3**
     (`logging.structured.format.console: logstash`) → NO requiere
     `logstash-logback-encoder`. El `traceId`/`spanId` se inyectan en el log
     por el puente de tracing.
   - **Centralización**: el módulo común aporta dependencias; la config va por
     env vars (`ZIPKIN_ENDPOINT`, `TRACING_SAMPLING_PROBABILITY`,
     `PROMETHEUS_ENABLED`) reutilizando el patrón de `common-security`.
2. **logstash-logback-encoder manual + logback.xml en cada servicio**:
   - Logs JSON buenos, pero **dependencia y XML duplicados en 6 servicios**, y no
     aporta metricas/trazas (mismas dependencias extra).
3. **Stack comercial/de nodos (SaaS tracing, New Relic, Datadog)**:
   - Rápido, pero introduce cuenta externa y credenciales; no aplica al
     clúster local Kind.
4. **Solo logs JSON sin métricas ni tracing**:
   - No cubre "métricas y trazabilidad" del cronograma. Descartado.

## Decisión

1. **Crear el módulo Maven `common-observability`** que agrupa las dependencias
   de observabilidad:
   - `micrometer-registry-prometheus`.
   - `micrometer-tracing-bridge-brave`.
   - `zipkin-reporter-brave`.
   - Un `@AutoConfiguration` con `@ConditionalOnProperty` para activar/desactivar
     el puente de tracing por env, siguiendo el patrón de `common-security`.
   - Se registra en el pom padre (modules + dependencyManagement) y cada
     microservicio solo agrega `<dependency>common-observability</dependency>`.
2. **Config por aplicación** (en `application.yml` de cada servicio, con
   defaults y sobreescritura por env):
   - `logging.structured.format.console: logstash` → logs **JSON line-delimited**
     en stdout (apto para Docker/K8s).
   - `management.endpoints.web.exposure.include: health,info,prometheus` →
     endpoint Prometheus.
   - `management.tracing.sampling.probability: ${TRACING_SAMPLING_PROBABILITY:1.0}`
     y `management.zipkin.tracing.endpoint: ${ZIPKIN_ENDPOINT:...}`.
   - El `api-gateway` también lo agrega: los filtros (JWT, rate limit) generan
     spans; los gateways son el punto de entrada de la traza.
3. **Infraestructura de observabilidad** (docker-compose y k8s):
   - **Zipkin** (`openzipkin/zipkin`) expuesto en 9411 (interno) — recibe spans.
   - **Prometheus** (`prom/prometheus`) con `prometheus.yml` que scrapea los 6
     servicios vía el Service DNS (k8s) / hostnames (compose); ConfigMap en k8s.
   - **Grafana** (`grafana/grafana`) en 3000 (solo dev/dashboards) con datasource
     apuntando a Prometheus.
   - Los deployments k8s agregan `ZIPKIN_ENDPOINT=http://zipkin:9411/api/v2/spans`
     y `TRACING_SAMPLING_PROBABILITY` en el ConfigMap (patrón ADR-0007: sin
     puertos internos expuestos a host, salvo Grafana que es UI de dev).
4. **Métricas de negocio**: se deja la instrumentación específica (contadores de
   pedidos creados, pagos rechazados, etc.) como mejora posterior; este ADR
   establece la **base** scrapeable (HTTP, JVM, Kafka, pool de conexiones).

## Consecuencias

### Positivas

- **Observabilidad transversal de un clic**: logs JSON, métricas Prometheus y
  trazas Zipkin desde el mismo stack; el `traceId` aparece en logs y spans.
- **Correlación distribuida**: un pedido se sigue por `traceId` desde el gateway
  hasta notificaciones (eventos Kafka incluidos).
- **Base para SLA/dashboards**: las métricas HTTP/JVM existen desde el día uno;
  agregar métricas de negocio luego es trivial (registry ya publica).
- **Sin dependencia externa**: todo corre en el clúster/compose local.
- **Bajo acoplamiento**: los microservicios solo agregan una dependencia común.

### Negativas / Compromisos

- **Más infraestructura**: Prometheus, Grafana y Zipkin se agregan al stack
  (compose + k8s). Carga de DaemonSets/pods pequeña en dev.
- **Overhead de sampling**: con `probability=1.0` en dev todo se muestra; en
  producción se baja (env) para no saturar Zipkin.
- **Logs JSON menos legibles a mano**: para consola se puede alternar a texto
  con `logging.structured.format.console` vacío en dev puntual.
- **Zipkin en memoria**: no hay persistencia de trazas (solo dev). Deuda para
  producción (Elasticsearch/Tempo + persistencia).
- **Grafana sin usuarios/config inicial**: solo datasource Prometheus; paneles a
  crear según necesidad.