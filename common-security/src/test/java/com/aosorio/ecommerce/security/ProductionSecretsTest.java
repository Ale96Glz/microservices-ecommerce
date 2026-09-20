package com.aosorio.ecommerce.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionSecretsTest {

    private static final String OK_JWT = "a-production-jwt-secret-of-at-least-32";
    private static final String PG_URL = "jdbc:postgresql://postgres:5432/auth_db";

    @Test
    void jwtValidoYPostgresSinUrlNoExigePassword() {
        assertThatCode(() -> ProductionSecrets.validate(OK_JWT, "", ""))
                .doesNotThrowAnyException();
    }

    @Test
    void jwtPorDefectoDeLabEsRechazado() {
        assertThatThrownBy(() -> ProductionSecrets.validate(
                ProductionSecrets.INSECURE_JWT_DEFAULT, PG_URL, "otro"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("change-me");
    }

    @Test
    void jwtCortoEsRechazado() {
        assertThatThrownBy(() -> ProductionSecrets.validate("corto", PG_URL, "otro"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32");
    }

    @Test
    void jwtVacioEsRechazado() {
        assertThatThrownBy(() -> ProductionSecrets.validate("  ", PG_URL, "otro"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("obligatorio");
    }

    @Test
    void passwordEcommerceConPostgresEsRechazado() {
        assertThatThrownBy(() -> ProductionSecrets.validate(OK_JWT, PG_URL, "ecommerce"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ecommerce");
    }

    @Test
    void passwordVacioConPostgresEsRechazado() {
        assertThatThrownBy(() -> ProductionSecrets.validate(OK_JWT, PG_URL, ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SPRING_DATASOURCE_PASSWORD");
    }

    @Test
    void h2NoExigePasswordDePostgres() {
        assertThatCode(() -> ProductionSecrets.validate(
                OK_JWT, "jdbc:h2:mem:auth_db", ""))
                .doesNotThrowAnyException();
    }

    @Test
    void postgresConPasswordPropioPasa() {
        assertThatCode(() -> ProductionSecrets.validate(OK_JWT, PG_URL, "s3gura-no-lab"))
                .doesNotThrowAnyException();
    }
}
