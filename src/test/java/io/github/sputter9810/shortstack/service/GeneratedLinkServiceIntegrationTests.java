package io.github.sputter9810.shortstack.service;

import java.time.Instant;
import java.util.UUID;

import io.github.sputter9810.shortstack.persistence.GeneratedLinkInserter;
import io.github.sputter9810.shortstack.persistence.ShortLink;
import io.github.sputter9810.shortstack.persistence.ShortLinkRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest
class GeneratedLinkServiceIntegrationTests {

    private static final Instant CREATED_AT = Instant.parse("2026-10-28T10:00:00.123456Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-10-29T10:00:00.654321Z");

    // REQUIRES_NEW commits independently: cleanup uses a per-test ownership marker,
    // not a rollback-only outer test transaction or deletion of arbitrary codes.
    private final String destination = "https://example.org/issue6-test/" + UUID.randomUUID();

    @Autowired
    private GeneratedLinkService service;

    @Autowired
    private GeneratedLinkInserter inserter;

    @Autowired
    private ShortLinkRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private ShortCodeGenerator generator;

    @AfterEach
    void cleanUpCommittedRows() {
        jdbc.update("DELETE FROM short_link WHERE destination_url = ?", destination);
    }

    @Test
    void persistsAndRetrievesAGeneratedCodeAfterCommit() {
        String code = new ShortCodeGenerator().generate();
        when(generator.generate()).thenReturn(code);

        ShortLink saved = service.create(destination, CREATED_AT, EXPIRES_AT);

        ShortLink retrieved = repository.findByCode(code).orElseThrow();
        assertThat(saved.getId()).isPositive();
        assertThat(retrieved.getId()).isEqualTo(saved.getId());
        assertThat(retrieved.getDestinationUrl()).isEqualTo(destination);
        assertThat(retrieved.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(retrieved.getExpiresAt()).isEqualTo(EXPIRES_AT);
    }

    @Test
    void rollsBackCollisionBeforePersistingFreshCandidate() {
        String taken = testCode();
        String fresh = testCode();
        ShortLink existing = inserter.insert(taken, destination, CREATED_AT, null);
        when(generator.generate()).thenReturn(taken, fresh);

        ShortLink saved = service.create(destination, CREATED_AT, EXPIRES_AT);

        assertThat(saved.getCode()).isEqualTo(fresh);
        assertThat(repository.findByCode(taken).orElseThrow().getId()).isEqualTo(existing.getId());
        assertThat(repository.findByCode(fresh).orElseThrow().getId()).isEqualTo(saved.getId());
        assertThat(ownedRowCount()).isEqualTo(2);
        verify(generator, times(2)).generate();
    }

    @Test
    void doesNotRetryAnUnrelatedDatabaseCheckViolation() {
        when(generator.generate()).thenReturn(testCode());

        assertThatThrownBy(() -> service.create(destination, CREATED_AT, CREATED_AT))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_short_link_expiration");

        verify(generator).generate();
        assertThat(ownedRowCount()).isZero();
        ShortLink subsequent = inserter.insert(testCode(), destination, CREATED_AT, null);
        assertThat(repository.findByCode(subsequent.getCode())).isPresent();
    }

    @Test
    void databaseRemainsUsableAfterFiveCollisions() {
        String taken = testCode();
        inserter.insert(taken, destination, CREATED_AT, null);
        when(generator.generate()).thenReturn(taken);

        assertThatThrownBy(() -> service.create(destination, CREATED_AT, null))
                .isInstanceOf(CodeGenerationExhaustedException.class);

        verify(generator, times(5)).generate();
        ShortLink subsequent = inserter.insert(testCode(), destination, CREATED_AT, null);
        assertThat(repository.findByCode(subsequent.getCode())).isPresent();
        assertThat(ownedRowCount()).isEqualTo(2);
    }

    @Test
    void insertionCommitsIndependentlyOfAnOuterRollback() {
        String code = testCode();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            inserter.insert(code, destination, CREATED_AT, null);
            status.setRollbackOnly();
        });

        assertThat(repository.findByCode(code)).isPresent();
    }

    @Test
    void coordinatorRejectsAnOuterTransactionBeforeGenerating() {
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(
                status -> service.create(destination, CREATED_AT, null)))
                .isInstanceOf(IllegalTransactionStateException.class);

        verifyNoInteractions(generator);
        assertThat(ownedRowCount()).isZero();
    }

    private int ownedRowCount() {
        return jdbc.queryForObject("SELECT count(*) FROM short_link WHERE destination_url = ?", Integer.class, destination);
    }

    private static String testCode() {
        return "I6" + UUID.randomUUID().toString().replace("-", "").substring(0, 6);
    }
}
