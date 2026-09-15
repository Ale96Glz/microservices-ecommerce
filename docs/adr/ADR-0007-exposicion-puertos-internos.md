# ADR-0007: Exposición de puertos internos y red por defecto

- **Estatus:** Aceptado
- **Fecha:** 2026-09

## Contexto y Problema

El api-gateway es la única puerta de entrada pública (Ingress → `api-gateway:8080`,
ver ADR-0005). Los microservicios internos (auth `8081`, catalogo `8082`, pedidos
`8083`, pagos `8084`, notificaciones `8085`) no deben ser alcanzables desde
fuera del clúster ni desde el host de desarrollo, salvo para diagnóstico puntual.

Se detectaron dos superficies de exposición:

1. **docker-compose (desarrollo local)**: todos los microservicios publican su
   puerto al host (`${AUTH_SERVICE_PORT:-8081}:8081`, ... `:8085`), además de
   infraestructura (`postgres:5432`, `kafka:9092`, `kafka-ui:8089`). Esto deja
   cada API accesible directamente en `localhost` sin pasar por el gateway.
2. **Kubernetes**: los Services internos son `ClusterIP` y no hay `NodePort`
   ni `LoadBalancer`, por lo que no se exponen fuera del clúster. Sin embargo
   no existe ninguna política de red que restrinja el tráfico entre pods
   dentro del namespace `ecommerce`.

## Opciones Evaluadas

1. **NetworkPolicies en Kubernetes**: restringir el tráfico entre pods
   (solamente gateway → servicios, pedidos → catalogo/auth, y servicios →
   postgres/kafka). Es la solución óptima a nivel red, **pero depende del CNI**.
   El clúster actual usa **`kindnet`** (Kind/Docker Desktop sobre WSL2), que
   **no implementa el cumplimiento de `NetworkPolicy`**: aplicar el manifiesto
   no produce ningún efecto de bloqueo. Requeriría instalar Calico o Cilium.
2. **No publicar puertos internos en docker-compose**: quitar los mapeos
   `ports:` de los microservicios; la comunicación interna usa la red de
   servicio propia de Compose (`auth-service:8081`, etc.) y el único ingreso
   externo queda en `api-gateway:8080`. Infraestructura de dev (postgres,
   kafka y kafka-ui) se puede mantener publicada con variables de entorno
   desactivables.
3. **Confiar únicamente en el JWT por servicio (ADR-0006)**: aceptar que cada
   servicio valida el token, por lo que un puerto expuesto queda inofensivo
   para quien no tenga token. No resuelve la limpieza de la superficie sino
   que la mitiga por aplicación.

## Decisión

Se combinan las opciones 2 y 3, y se pospone la 1:

1. **Red por defecto (aplica):**
   - **docker-compose** deja de publicar los puertos `8081`–`8085` de los
     microservicios. Solo `api-gateway` se publica en el host (`8080`), como
     única entrada externa lógica. Se conservan las URLs internas de la red de
     Compose (`http://<servicio>:<puerto>`) en las variables de entorno.
   - **Kubernetes**: se mantiene el estado actual como correcto: Services
     `ClusterIP` para los internos y **un único Ingress** hacia `api-gateway`.
     No se agregan `NodePort` ni `LoadBalancer` a los microservicios.
   - Para diagnóstico puntual se usa `kubectl port-forward` (no se publica
     puerto de forma permanente).
2. **Defensa por aplicación (ya existe)**: el ADR-0006 exige JWT válido en
   cada microservicio; un acceso directo a un puerto interno no otorga acceso
   sin token. Esta es la mitigación activa frente a exposiciones accidentales.
3. **Deuda técnica documentada**: las `NetworkPolicies` reales quedan
   pendientes de un CNI que las haga cumplir (Calico/Cilium o un clúster
   gestionado). Registrado en la sección "Consecuencias".
4. **Exposición de infraestructura en dev** (postgres, kafka, kafka-ui):
   permanece publicada por conveniencia de desarrollo, parametrizada con
   variables de entorno y documentada como no aplicable a entornos
   productivos.

## Consecuencias

### Positivas

- **Superficie mínima**: fuera de la red del clúster/Compose solo existe el
  gateway; los microservicios no son alcanzables desde el host.
- **Doble defensa**: incluso si un Service interno se expone por error, el
  JWT por servicio (ADR-0006) sigue bloqueando el acceso sin token.
- **Cero ingeniería extra**: no se requiere cambio de CNI ni movimientos de
  red para obtener el primer nivel de protección.

### Negativas / Compromisos

- **Diagnóstico local más incómodo**: probar un microservicio aislado en
  Compose requiere exponer su puerto a mano (variable de entorno) o usar
  `docker exec`/`kubectl port-forward`.
- **NetworkPolicy no efectivas con el CNI actual**: `kindnet` no aplica
  restricción; si se quiere aislamiento de red real entre pods hace falta
  instalar Calico/Cilium o migrar a un clúster gestionado (deuda técnica
  abierta).
- **Dev local con infra publicada**: postgres (5432) y Kafka (9092) quedan
  alcanzables en `localhost` para herramientas de desarrollo; es una elección
  deliberada del entorno local, no válida para producción.