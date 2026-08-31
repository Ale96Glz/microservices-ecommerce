# C4 — Nivel 1: Diagrama de Contexto

```mermaid
graph TD
    Cliente["Usuario final<br/>navegador / app móvil"]
    Admin["Administrador del sistema"]
    PagoExt["Procesador de pagos externo<br/>(simulado)"]

    subgraph Sistema["Sistema Ecommerce Microservices"]
        EC["Ecommerce Platform<br/>Spring Boot / Java 21"]
    end

    Admin -->|"HTTPS / REST<br/>B2B"| EC
    Cliente -->|"HTTPS / REST / JSON<br/>operaciones de compra"| EC
    EC -->|"Débito de tarjeta<br/>HTTPS / API"| PagoExt
```

**Lectura:** los actores externos (usuario final y administrador) interactúan con
la plataforma a través de HTTP. El procesador externo de pagos es una integración
simulada en esta etapa del proyecto.

**Notas:**
- Todo el tráfico entra por el `api-gateway` (ver Nivel 2).
- La plataforma se despliega en Kubernetes con un `Ingress` (ADR-0005).
