package io.github.sputter9810.shortstack.service;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AliasConcurrencyTests {
    @Autowired
    private LinkService service;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void concurrentRequestsCommitOnlyOneAlias() throws Exception {
        String alias = UUID.randomUUID().toString().replace("-", "");
        String destination = "https://example.org/alias-test/" + UUID.randomUUID();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<String> request = () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Start barrier timed out");
                }
                try {
                    service.create(destination, alias);
                    return "created";
                } catch (AliasConflictException expected) {
                    return "conflict";
                }
            };
            var first = executor.submit(request);
            var second = executor.submit(request);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(java.util.List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("created", "conflict");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM short_link WHERE code = ?", Integer.class, alias)).isEqualTo(1);
        } finally {
            start.countDown();
            jdbc.update("DELETE FROM short_link WHERE destination_url = ?", destination);
        }
    }
}
