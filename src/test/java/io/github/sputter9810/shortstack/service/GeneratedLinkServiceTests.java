package io.github.sputter9810.shortstack.service;

import java.sql.SQLException;
import java.time.Instant;
import java.util.stream.Stream;

import io.github.sputter9810.shortstack.persistence.GeneratedLinkInserter;
import io.github.sputter9810.shortstack.persistence.ShortLink;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.TransactionSystemException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class GeneratedLinkServiceTests {

    private static final String DESTINATION = "https://example.org/unit-test";
    private static final Instant CREATED_AT = Instant.parse("2026-10-28T10:00:00.123456Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-10-29T10:00:00.654321Z");

    private ShortCodeGenerator generator;
    private GeneratedLinkInserter inserter;
    private GeneratedLinkService service;

    @BeforeEach
    void setUp() {
        generator = mock(ShortCodeGenerator.class);
        inserter = mock(GeneratedLinkInserter.class);
        service = new GeneratedLinkService(generator, inserter);
    }

    @Test
    void returnsFirstSuccessfulInsertion() {
        ShortLink saved = new ShortLink("AbCd1234", DESTINATION, CREATED_AT, EXPIRES_AT);
        when(generator.generate()).thenReturn("AbCd1234");
        when(inserter.insert("AbCd1234", DESTINATION, CREATED_AT, EXPIRES_AT)).thenReturn(saved);

        assertThat(service.create(DESTINATION, CREATED_AT, EXPIRES_AT)).isSameAs(saved);

        verify(generator).generate();
        verify(inserter).insert("AbCd1234", DESTINATION, CREATED_AT, EXPIRES_AT);
        verifyNoMoreInteractions(generator, inserter);
    }

    @Test
    void retriesWithAFreshCandidateAndUnchangedValuesAfterCollision() {
        ShortLink saved = new ShortLink("Fresh123", DESTINATION, CREATED_AT, EXPIRES_AT);
        when(generator.generate()).thenReturn("Taken123", "Fresh123");
        when(inserter.insert("Taken123", DESTINATION, CREATED_AT, EXPIRES_AT)).thenThrow(collision());
        when(inserter.insert("Fresh123", DESTINATION, CREATED_AT, EXPIRES_AT)).thenReturn(saved);

        assertThat(service.create(DESTINATION, CREATED_AT, EXPIRES_AT)).isSameAs(saved);

        var order = inOrder(generator, inserter);
        order.verify(generator).generate();
        order.verify(inserter).insert("Taken123", DESTINATION, CREATED_AT, EXPIRES_AT);
        order.verify(generator).generate();
        order.verify(inserter).insert("Fresh123", DESTINATION, CREATED_AT, EXPIRES_AT);
        order.verifyNoMoreInteractions();
    }

    @Test
    void permitsSuccessOnTheFifthAttempt() {
        ShortLink saved = new ShortLink("Code0005", DESTINATION, CREATED_AT, EXPIRES_AT);
        when(generator.generate()).thenReturn("Code0001", "Code0002", "Code0003", "Code0004", "Code0005");
        when(inserter.insert(any(), eq(DESTINATION), eq(CREATED_AT), eq(EXPIRES_AT)))
                .thenThrow(collision(), collision(), collision(), collision()).thenReturn(saved);

        assertThat(service.create(DESTINATION, CREATED_AT, EXPIRES_AT)).isSameAs(saved);

        verify(generator, times(5)).generate();
        for (int attempt = 1; attempt <= 5; attempt++) {
            verify(inserter).insert("Code000" + attempt, DESTINATION, CREATED_AT, EXPIRES_AT);
        }
        verifyNoMoreInteractions(generator, inserter);
    }

    @Test
    void exhaustsAfterExactlyFiveCollisions() {
        DataIntegrityViolationException collision = collision();
        when(generator.generate()).thenReturn("Code0001", "Code0002", "Code0003", "Code0004", "Code0005");
        when(inserter.insert(any(), eq(DESTINATION), eq(CREATED_AT), eq(EXPIRES_AT))).thenThrow(collision);

        assertThatThrownBy(() -> service.create(DESTINATION, CREATED_AT, EXPIRES_AT))
                .isInstanceOf(CodeGenerationExhaustedException.class).hasCause(collision);

        verify(generator, times(5)).generate();
        verify(inserter, times(5)).insert(any(), eq(DESTINATION), eq(CREATED_AT), eq(EXPIRES_AT));
        verifyNoMoreInteractions(generator, inserter);
    }

    @ParameterizedTest
    @MethodSource("unrelatedFailures")
    void propagatesFailuresWithoutRetries(RuntimeException failure) {
        when(generator.generate()).thenReturn("AbCd1234");
        when(inserter.insert("AbCd1234", DESTINATION, CREATED_AT, EXPIRES_AT)).thenThrow(failure);

        assertThatThrownBy(() -> service.create(DESTINATION, CREATED_AT, EXPIRES_AT)).isSameAs(failure);

        verify(generator).generate();
        verify(inserter).insert("AbCd1234", DESTINATION, CREATED_AT, EXPIRES_AT);
        verifyNoMoreInteractions(generator, inserter);
    }

    @Test
    void findsCollisionEvidenceUnderAdditionalCauseWrappers() {
        DataIntegrityViolationException wrapped = new DataIntegrityViolationException("translated",
                new IllegalStateException("intermediate", collision()));
        when(generator.generate()).thenReturn("Taken123", "Fresh123");
        ShortLink saved = new ShortLink("Fresh123", DESTINATION, CREATED_AT, EXPIRES_AT);
        when(inserter.insert(any(), eq(DESTINATION), eq(CREATED_AT), eq(EXPIRES_AT)))
                .thenThrow(wrapped).thenReturn(saved);

        assertThat(service.create(DESTINATION, CREATED_AT, EXPIRES_AT)).isSameAs(saved);
        verify(generator, times(2)).generate();
    }

    @Test
    void safelyRejectsACyclicCauseChainWithoutEvidence() {
        IllegalStateException inner = new IllegalStateException("cycle");
        DataIntegrityViolationException failure = new DataIntegrityViolationException("translated", inner);
        inner.initCause(failure);
        when(generator.generate()).thenReturn("AbCd1234");
        when(inserter.insert("AbCd1234", DESTINATION, CREATED_AT, EXPIRES_AT)).thenThrow(failure);

        assertThatThrownBy(() -> service.create(DESTINATION, CREATED_AT, EXPIRES_AT)).isSameAs(failure);
        verify(generator).generate();
    }

    private static Stream<RuntimeException> unrelatedFailures() {
        return Stream.of(
                integrity("23505", "pk_short_link"),
                integrity("23514", "uq_short_link_code"),
                integrity("23502", "uq_short_link_code"),
                integrity(null, "uq_short_link_code"),
                integrity("23505", null),
                integrity("23505", "UQ_SHORT_LINK_CODE"),
                new DataIntegrityViolationException("no database evidence"),
                new DataIntegrityViolationException("SQLSTATE only", new SQLException("duplicate", "23505")),
                new DataAccessResourceFailureException("connection unavailable"),
                new TransactionSystemException("transaction failure", collision()));
    }

    private static DataIntegrityViolationException collision() {
        return integrity("23505", "uq_short_link_code");
    }

    private static DataIntegrityViolationException integrity(String state, String constraint) {
        return new DataIntegrityViolationException("database rejection",
                new ConstraintViolationException("constraint rejection", new SQLException("rejected", state), constraint));
    }
}
