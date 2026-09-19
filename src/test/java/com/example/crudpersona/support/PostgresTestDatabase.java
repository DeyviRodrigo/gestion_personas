package com.example.crudpersona.support;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import java.sql.DriverManager;
import java.util.*;

/** PostgreSQL temporal por clase. Un servidor externo solo se usa si se indica TEST_DB_URL. */
public class PostgresTestDatabase implements AfterAllCallback {
    private final String schema = "gp_test_" + UUID.randomUUID().toString().replace("-", "");
    private String baseUrl = System.getenv("TEST_DB_URL");
    private String usuario = System.getenv().getOrDefault("TEST_DB_USER", "postgres");
    private String password = System.getenv().getOrDefault("TEST_DB_PASSWORD", "postgres");
    private boolean creado;
    private EmbeddedPostgres temporal;

    public Map<String, Object> properties() {
        crear();
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("spring.config.import", "");
        p.put("spring.datasource.url", baseUrl + (baseUrl.contains("?") ? "&" : "?") + "currentSchema=" + schema);
        p.put("spring.datasource.username", usuario);
        p.put("spring.datasource.password", password);
        p.put("spring.flyway.url", p.get("spring.datasource.url"));
        p.put("spring.flyway.user", usuario);
        p.put("spring.flyway.password", password);
        p.put("spring.flyway.default-schema", schema);
        p.put("spring.flyway.schemas", schema);
        p.put("spring.jpa.properties.hibernate.default_schema", schema);
        p.put("spring.jpa.show-sql", "false");
        p.put("app.audit-user", "pruebas");
        return p;
    }

    public void configure(DynamicPropertyRegistry registry) {
        properties().forEach((key, value) -> registry.add(key, () -> value));
    }

    private synchronized void crear() {
        if (creado) { return; }
        try {
            if (baseUrl == null || baseUrl.isBlank()) {
                usuario = "postgres";
                password = "postgres";
                temporal = EmbeddedPostgres.builder().setPort(0)
                        .setServerConfig("listen_addresses", "localhost").start();
                baseUrl = temporal.getJdbcUrl("postgres", "postgres");
            }
            try (var c = DriverManager.getConnection(baseUrl, usuario, password); var sql = c.createStatement()) {
                sql.execute("CREATE SCHEMA " + schema);
                creado = true;
            }
        } catch (Exception e) {
            if (temporal != null) {
                try { temporal.close(); } catch (Exception cierre) { e.addSuppressed(cierre); }
            }
            throw new IllegalStateException("No se pudo preparar PostgreSQL de pruebas. Revisa el error o configura TEST_DB_URL/USER/PASSWORD.", e);
        }
    }

    @Override public void afterAll(ExtensionContext context) throws Exception {
        try {
            if (creado && schema.matches("gp_test_[a-f0-9]{32}")) {
                try (var c = DriverManager.getConnection(baseUrl, usuario, password); var sql = c.createStatement()) {
                    sql.execute("DROP SCHEMA " + schema + " CASCADE");
                }
            }
        } finally {
            if (temporal != null) { temporal.close(); }
        }
    }
}
