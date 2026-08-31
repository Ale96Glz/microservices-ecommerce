# ADR-0005: Exposición del api-gateway mediante Ingress en Kubernetes

- **Estatus:** Aceptado
- **Fecha:** 2026-08-30

## Contexto y Problema

Dentro del clúster Kubernetes, el `api-gateway` se despliega como un `Service` de
tipo `ClusterIP` (`api-gateway-service.yaml`), por lo que **no es accesible desde
fuera del clúster**. Para completar la `Fase 1 — Plataforma y despliegue` de la
hoja de ruta, falta el punto de entrada externo del ecosistema.

Los health checks (`startupProbe`, `readinessProbe`, `livenessProbe` sobre
`/actuator/health*`) ya estaban implementados en los deployments; el único hueco
era cómo exponer HTTP hacia el exterior.

Se evaluaron tres mecanismos de exposición:

1. **`Service` tipo `NodePort`**: expone el puerto en cada nodo del clúster, pero
   usa puertos no estándar (30000-32767), no ofrece enrutamiento por host/path y
   es incómodo con load balancers externos.
2. **`Service` tipo `LoadBalancer`**: crea un LB cloud por servicio; es caro y
   no homogéneo entre proveedores (GKE/EKS/AKS/on-prem).
3. **`Ingress`** con `ingressClassName: nginx` e `ingress-nginx`: enrutamiento
   HTTP/7 centralizado, un solo punto de entrada, soporte multiplataforma y base
   para agregar TLS/HTTPS más adelante (`Fase 3`).

## Decisión

Se crea `k8s/ingress.yaml` que expone el `api-gateway` hacia el exterior:

- Clase de ingress: `nginx` (controlador `ingress-nginx`).
- Regla: **path `PathPrefix /`** → `service api-gateway` puerto `8080`, delegando
  el enrutamiento interno por path (`/api/v1/...`) al propio gateway.
- Anotación `nginx.ingress.kubernetes.io/proxy-body-size: 10m` para permitir
  cuerpos de petición razonables (creación de pedidos/productos).
- Se añade `ingress.yaml` al `kustomization.yaml` del namespace `ecommerce`.

Se mantiene el `Service` del gateway como `ClusterIP`: el Ingress apunta a él, y
no se abren puertos internos de los demás servicios hacia el exterior.

## Consecuencias

### Positivas

- **Único punto de entrada externo** (gateway en `:8080`), coherente con el
  modelo de seguridad de `ADR-0002`.
- **Los microservicios internos permanecen como `ClusterIP`**, sin exposición
  directa, reduciendo superficie de ataque.
- **Ruta clara para HTTPS**: añadir `tls` en el Ingress en `Fase 3` sin cambios
  en los deployments.
- **Multiplataforma**: `ingress-nginx` funciona en GKE, EKS, AKS y on-prem.

### Negativas / Compromisos

- **Requiere un controlador de Ingress instalado** (`ingress-nginx`); sin él, el
  recurso se crea pero no enruta tráfico.
- **Un solo Ingress por cluster** concentra el tráfico; los ajustes de
  `limit/rate-limiting` quedan pendientes (`Fase 3`).
- **Sin HTTPS aún**: el tráfico externo va en claro hasta configurar el `tls`
  del Ingress (pendiente en `Fase 3`).
- **Ingress-class dependiente**: si se adoptara otro controlador (Traefik, etc.)
  habría que migrar la clase y anotaciones.
