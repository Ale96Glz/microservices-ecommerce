-- PVC anterior a ADR-0016: tabla pago sin columna intento ni unique (pedido_id, intento).

ALTER TABLE pago ADD COLUMN IF NOT EXISTS intento INTEGER;

UPDATE pago SET intento = 1 WHERE intento IS NULL;

ALTER TABLE pago ALTER COLUMN intento SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_pago_pedido_intento ON pago (pedido_id, intento);
