# Ecommerce Microservices

Estructura base del monorepo. La lógica de cada servicio se implementa a partir de aquí.

## Módulos

- `common-events` — DTOs de eventos compartidos
- `api-gateway` — gateway HTTP
- `auth-service` — autenticación / JWT
- `catalogo-service` — productos
- `pedidos-service` — pedidos + eventos
- `pagos-service` — pagos + eventos
- `notificaciones-service` — notificaciones

## Infra local

```bash
cp .env.example .env
docker compose up -d
```

Levanta Postgres (con DBs por servicio) y Kafka.
