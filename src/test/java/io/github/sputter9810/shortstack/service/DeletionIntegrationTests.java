package io.github.sputter9810.shortstack.service;

import java.time.Instant;
import java.util.UUID;
import io.github.sputter9810.shortstack.persistence.GeneratedLinkInserter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class DeletionIntegrationTests {
    @Autowired private LinkService service;
    @Autowired private GeneratedLinkInserter inserter;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void deletesExpiredRowAndReleasesCodeForReuse() {
        String code = UUID.randomUUID().toString().replace("-", "");
        String destination = "https://example.org/delete-test/" + UUID.randomUUID();
        try {
            inserter.insert(code, destination, Instant.parse("2020-01-01T00:00:00Z"), Instant.parse("2020-01-02T00:00:00Z"));
            assertThat(service.get(code).expired()).isTrue();
            service.delete(code);
            assertThatThrownBy(() -> service.get(code)).isInstanceOf(LinkNotFoundException.class);
            assertThatThrownBy(() -> service.resolve(code)).isInstanceOf(LinkNotFoundException.class);
            assertThatThrownBy(() -> service.delete(code)).isInstanceOf(LinkNotFoundException.class);
            assertThat(service.create(destination, code).code()).isEqualTo(code);
            assertThat(service.resolve(code)).isEqualTo(destination);
        } finally {
            jdbc.update("DELETE FROM short_link WHERE destination_url = ?", destination);
        }
    }
}
