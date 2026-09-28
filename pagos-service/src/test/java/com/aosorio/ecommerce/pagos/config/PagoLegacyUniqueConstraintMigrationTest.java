package com.aosorio.ecommerce.pagos.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PagoLegacyUniqueConstraintMigrationTest {

    @Test
    void aceptaIdentificadoresPostgresSimples() {
        assertTrue(PagoLegacyUniqueConstraintMigration.esIdentificadorSeguro("pago_pedido_id_key"));
        assertTrue(PagoLegacyUniqueConstraintMigration.esIdentificadorSeguro("_tmp"));
    }

    @Test
    void rechazaValoresInsegurosOVacios() {
        assertFalse(PagoLegacyUniqueConstraintMigration.esIdentificadorSeguro(null));
        assertFalse(PagoLegacyUniqueConstraintMigration.esIdentificadorSeguro(""));
        assertFalse(PagoLegacyUniqueConstraintMigration.esIdentificadorSeguro("pago; DROP TABLE pago"));
        assertFalse(PagoLegacyUniqueConstraintMigration.esIdentificadorSeguro("pago\"x"));
        assertFalse(PagoLegacyUniqueConstraintMigration.esIdentificadorSeguro("1pago"));
    }
}
