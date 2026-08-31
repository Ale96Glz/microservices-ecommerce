# ADR-0001: Arquitectura de microservicios con Spring Boot

- **Estatus:** Aceptado
- **Fecha:** 2023-10 (iniciativa del proyecto)

## Contexto y Problema

Se necesita construir una plataforma de comercio electrónico capaz de gestionar
usuarios, catálogo, pedidos, pagos y notificaciones. Los requisitos favorecen la
evolución independiente de cada dominio funcional:

- Los equipos de negocio tienen ciclos de despliegue distintos (pagos vs.
  catálogo evolucionan a ritmos diferentes).
- Se anticipa un crecimiento de carga asimétrico: el catálogo recibe mucho tráfico
  de lectura pública mientras que los pagos concentran tráfico transaccional.
- El proyecto no cuenta con restricción fuerte de latencia intra-servicio, por lo
  que la latencia de red añadida por llamadas entre servicios es aceptable.

Se evaluaron tres opciones:

1. **Monolito modular** (Spring Boot + módulos Maven): menor complejidad
   operativa, pero acopla el ciclo de vida de todos los dominios.
2. **Arquitectura orientada a servicios** clásica (ESB/soap): rechazada por
   rigidez e infraestructura pesada.
3. **Microservicios** independientes desplegados por separado: mayor autonomía y
   escalado independiente, a costa de complejidad distribuida.

## Decisión

Se adopta un **monorepo Maven multi-módulo** con **microservicios
independientes** sobre Spring Boot 3.3.5 / Java 21. Cada servicio es un artefacto
desplegable por separado:

| Módulo | Responsabilidad | Puerto |
|---|---|---|
| `api-gateway` | Punto de entrada HTTP, rutas, validación JWT | 8080 |
| `auth-service` | Registro, login, JWT, usuarios | 8081 |
| `catalogo-service` | Productos y categorías (stock, precio, estado) | 8082 |
| `pedidos-service` | Creación, consulta y cancelación de pedidos | 8083 |
| `pagos-service` | Procesamiento e idempotencia de pagos | 8084 |
| `notificaciones-service` | Registro y consulta de notificaciones | 8085 |
| `common-events` | Eventos compartidos (`OrderCreatedEvent`, `PaymentProcessedEvent`) | - |

Las justificaciones técnicas principales:

- **Aislamiento de datos y despliegue**: cada servicio posee su propia base de
  datos (ver `ADR-0004`), lo que evita acoplamiento de esquemas entre dominios.
- **Escalado independiente**: el catálogo puede escalar horizontalmente sin
  replicar la lógica de pagos.
- **Fronteras de dominio explícitas**: el modelo de eventos compartidos
  (`common-events`) fuerza contratos de integración versionados.
- **Selección de tecnología alineada**: todo el stack es JVM/Spring, lo que
  reduce fricción de idioma entre servicios; se acepta la pérdida de
  heterogeneidad tecnológica a cambio de productividad.

## Consecuencias

### Positivas

- Autonomía de despliegue: un fallo en `notificaciones-service` no derriba la
  creación de pedidos ni pagos.
- Escalado de lectura del catálogo sin acoplar el resto.
- Límites de dominio claramente modelados en entidades y contratos.
- Compilación y publicación de imágenes orquestadas por Maven (multi-módulo) y CI
  (GHCR).

### Negativas / Compromisos

- **Complejidad distribuida**: consistencia eventual, latencia de red y fallos
  parciales entre servicios exigen manejo explícito.
- **Duplicación de seguridad**: cada servicio reimplementa el helper `GatewayAuth`
  sobre headers inyectados (equilibrado con `ADR-0002`).
- **Sin service discovery**: el enrutado usa URLs estáticas vía Spring Cloud
  Gateway y `common-events`; no hay Eureka/Nacos, lo que limita el failover
  automático intra-cluster.
- **Configuración duplicada**: la plantilla de dependencias (Lombok, springdoc,
  actuator) está repetida en cada `pom.xml`.
- **Pruebas distribuidas**: aún no hay pruebas de contrato ni de integración
  entre servicios (`Fase 4` de la hoja de ruta), por lo que los acuerdos entre
  servicios dependen de disciplina manual.
