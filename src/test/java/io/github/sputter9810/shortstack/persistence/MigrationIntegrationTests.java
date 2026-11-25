package io.github.sputter9810.shortstack.persistence;

import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class MigrationIntegrationTests {
    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void migratesAnEmptyIsolatedSchemaAndDoesNotReapplyV1() {
        String schema = "issue14_" + UUID.randomUUID().toString().replace("-", "");
        Flyway migrations = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).load();
        try {
            assertThat(migrations.migrate().migrationsExecuted).isEqualTo(1);
            assertThat(migrations.migrate().migrationsExecuted).isZero();
            assertThat(jdbc.queryForList("SELECT column_name FROM information_schema.columns WHERE table_schema=? AND table_name='short_link' ORDER BY ordinal_position", String.class, schema))
                    .containsExactly("id", "code", "destination_url", "created_at", "expires_at");
            assertThat(jdbc.queryForList("SELECT conname FROM pg_constraint WHERE conrelid = ?::regclass", String.class, schema + ".short_link"))
                    .containsExactlyInAnyOrder("pk_short_link", "uq_short_link_code", "ck_short_link_code_format", "ck_short_link_reserved_code",
                            "ck_short_link_destination_nonempty", "ck_short_link_expiration", "ck_short_link_created_range", "ck_short_link_expiration_range");
            assertThat(jdbc.queryForObject("SELECT identity_generation FROM information_schema.columns WHERE table_schema=? AND table_name='short_link' AND column_name='id'", String.class, schema)).isEqualTo("ALWAYS");
            assertThat(jdbc.queryForList("SELECT datetime_precision FROM information_schema.columns WHERE table_schema=? AND table_name='short_link' AND data_type='timestamp with time zone'", Integer.class, schema)).containsExactly(6, 6);
        } finally {
            // Schema name is generated here, never supplied by a request or environment.
            jdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }
}
