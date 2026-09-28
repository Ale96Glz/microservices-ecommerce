package com.aosorio.ecommerce.pagos.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
@Slf4j
public class PagoLegacyUniqueConstraintMigration implements ApplicationRunner {

    private static final Pattern IDENTIFICADOR_POSTGRES = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) throws SQLException {
        String producto;
        try (Connection connection = dataSource.getConnection()) {
            producto = connection.getMetaData().getDatabaseProductName();
        }
        if (producto == null || !producto.toLowerCase().contains("postgres")) {
            log.info("BD no es PostgreSQL ({}); no se migra la unicidad antigua de pago", producto);
            return;
        }

        List<String> restricciones = jdbcTemplate.queryForList("""
                SELECT con.conname
                FROM pg_constraint con
                JOIN pg_class rel ON rel.oid = con.conrelid
                JOIN pg_namespace nsp ON nsp.oid = rel.relnamespace
                WHERE rel.relname = 'pago'
                  AND nsp.nspname = 'public'
                  AND con.contype = 'u'
                  AND EXISTS (
                        SELECT 1 FROM unnest(con.conkey) cols
                        JOIN pg_attribute a
                          ON a.attrelid = con.conrelid AND a.attnum = cols
                        WHERE a.attname = 'pedido_id')
                  AND NOT EXISTS (
                        SELECT 1 FROM unnest(con.conkey) cols
                        JOIN pg_attribute a
                          ON a.attrelid = con.conrelid AND a.attnum = cols
                        WHERE a.attname <> 'pedido_id')
                """, String.class);
        for (String nombre : restricciones) {
            if (!esIdentificadorSeguro(nombre)) {
                log.warn("Se omite DROP CONSTRAINT: nombre no es un identificador Postgres valido: {}", nombre);
                continue;
            }
            jdbcTemplate.execute("ALTER TABLE pago DROP CONSTRAINT IF EXISTS \"" + nombre + "\"");
            log.info("Restriccion unica antigua '{}' de la tabla pago eliminada", nombre);
        }
        if (restricciones.isEmpty()) {
            log.info("No hay restriccion unica antigua de pago que migrar");
        }
    }

    static boolean esIdentificadorSeguro(String nombre) {
        return nombre != null && IDENTIFICADOR_POSTGRES.matcher(nombre).matches();
    }
}