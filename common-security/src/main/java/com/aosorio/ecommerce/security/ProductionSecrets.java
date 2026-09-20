package com.aosorio.ecommerce.security;

/**
 * Reglas de secretos para el profile {@code prod}. El lab puede usar defaults;
 * producción no arranca con JWT {@code change-me} ni password {@code ecommerce}.
 */
public final class ProductionSecrets {

    public static final String INSECURE_JWT_DEFAULT =
            "change-me-to-a-long-random-secret-key-at-least-32-chars";
    public static final String INSECURE_POSTGRES_DEFAULT = "ecommerce";
    public static final int MIN_JWT_SECRET_LENGTH = 32;

    private ProductionSecrets() {
    }

    public static void validate(String jwtSecret, String datasourceUrl, String datasourcePassword) {
        validateJwt(jwtSecret);
        if (usesPostgres(datasourceUrl)) {
            validatePostgresPassword(datasourcePassword);
        }
    }

    static void validateJwt(String jwtSecret) {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException(
                    "Profile prod: JWT_SECRET / jwt.secret es obligatorio");
        }
        if (jwtSecret.equals(INSECURE_JWT_DEFAULT)) {
            throw new IllegalStateException(
                    "Profile prod: jwt.secret no puede ser el valor por defecto de lab (change-me-…)");
        }
        if (jwtSecret.length() < MIN_JWT_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "Profile prod: jwt.secret debe tener al menos " + MIN_JWT_SECRET_LENGTH + " caracteres");
        }
    }

    static void validatePostgresPassword(String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "Profile prod: SPRING_DATASOURCE_PASSWORD es obligatorio con PostgreSQL");
        }
        if (INSECURE_POSTGRES_DEFAULT.equals(password)) {
            throw new IllegalStateException(
                    "Profile prod: el password de Postgres no puede ser el default de lab (ecommerce)");
        }
    }

    static boolean usesPostgres(String datasourceUrl) {
        return datasourceUrl != null && datasourceUrl.contains("postgresql");
    }
}
