package io.github.sputter9810.shortstack.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
class ConcurrencyReviewTests {
    @Autowired private GeneratedLinkService generated;
    @Autowired private LinkService links;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private ShortCodeGenerator generator;
    private final String destination = "https://example.org/concurrency-test/" + UUID.randomUUID();

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM short_link WHERE destination_url = ?", destination);
    }

    @Test
    void concurrentGeneratedCandidatesCollideThenRetryInFreshTransaction() throws Exception {
        String prefix = UUID.randomUUID().toString().replace("-", "").substring(0, 7);
        String shared = prefix + "A";
        String fresh = prefix + "B";
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger draws = new AtomicInteger();
        when(generator.generate()).thenAnswer(invocation -> {
            if (draws.getAndIncrement() < 2) {
                ready.countDown();
                if (!release.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Candidate barrier timed out");
                }
                return shared;
            }
            return fresh;
        });
        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<String> request = () -> generated.create(destination,
                    Instant.parse("2026-01-01T00:00:00.123456Z"), null).getCode();
            var first = executor.submit(request);
            var second = executor.submit(request);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            release.countDown();
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(shared, fresh);
            verify(generator, times(3)).generate();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM short_link WHERE destination_url=?", Integer.class, destination)).isEqualTo(2);
        } finally {
            release.countDown();
        }
    }

    @Test
    void concurrentDeletesReportOneSuccessAndOneMissing() throws Exception {
        String code = UUID.randomUUID().toString().replace("-", "");
        links.create(destination, code);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<String> request = () -> {
                ready.countDown();
                if (!release.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Delete barrier timed out");
                }
                try {
                    links.delete(code);
                    return "deleted";
                } catch (LinkNotFoundException expected) {
                    return "missing";
                }
            };
            var first = executor.submit(request);
            var second = executor.submit(request);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            release.countDown();
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("deleted", "missing");
            assertThatThrownBy(() -> links.resolve(code)).isInstanceOf(LinkNotFoundException.class);
        } finally {
            release.countDown();
        }
    }
}
