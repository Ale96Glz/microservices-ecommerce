#!/bin/bash
set -euo pipefail
# Crea los tópicos de contrato (ADR-0018). Idempotente.
BOOTSTRAP="${KAFKA_BOOTSTRAP:-kafka:29092}"

topics=(
  order-created
  order-created.DLT
  payment-processed
  payment-processed.DLT
  restock-requested
  restock-requested.DLT
)

for t in "${topics[@]}"; do
  kafka-topics.sh --bootstrap-server "$BOOTSTRAP" \
    --create --if-not-exists --topic "$t" \
    --partitions 1 --replication-factor 1
  echo "OK topic $t"
done

kafka-topics.sh --bootstrap-server "$BOOTSTRAP" --list
