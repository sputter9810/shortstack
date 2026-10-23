package io.github.sputter9810.shortstack.persistence;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

@SpringBootTest
@Transactional
class ShortLinkRepositoryTests {

    private static final Instant CREATED_AT = Instant.parse("2026-10-23T10:00:00.123456Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-10-24T10:00:00.654321Z");
    private static final String DESTINATION = "https://example.org/articles?id=42#intro";

    @Autowired
    private ShortLinkRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void persistsAndRetrievesByCodeWithTimestamps() {
        String code = testCode();
        ShortLink saved = repository.saveAndFlush(new ShortLink(code, DESTINATION, CREATED_AT, EXPIRES_AT));
        assertThat(saved.getId()).isPositive();
        entityManager.clear();

        ShortLink retrieved = repository.findByCode(code).orElseThrow();

        assertThat(retrieved.getId()).isEqualTo(saved.getId());
        assertThat(retrieved.getCode()).isEqualTo(code);
        assertThat(retrieved.getDestinationUrl()).isEqualTo(DESTINATION);
        assertThat(retrieved.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(retrieved.getExpiresAt()).isEqualTo(EXPIRES_AT);
    }

    @Test
    void returnsEmptyForAbsentCode() {
        assertThat(repository.findByCode(testCode())).isEmpty();
    }

    @Test
    void roundTripsNullExpiration() {
        String code = testCode();
        repository.saveAndFlush(new ShortLink(code, DESTINATION, CREATED_AT, null));
        entityManager.clear();

        ShortLink retrieved = repository.findByCode(code).orElseThrow();

        assertThat(retrieved.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(retrieved.getExpiresAt()).isNull();
    }

    @Test
    void looksUpCaseDistinctCodesExactly() {
        String lowerCode = testCode();
        String upperCode = lowerCode.toUpperCase(Locale.ROOT);
        ShortLink lower = repository.saveAndFlush(new ShortLink(lowerCode, DESTINATION, CREATED_AT, null));
        entityManager.clear();
        assertThat(repository.findByCode(upperCode)).isEmpty();

        ShortLink upper = repository.saveAndFlush(new ShortLink(upperCode, DESTINATION, CREATED_AT, null));
        entityManager.clear();

        assertThat(repository.findByCode(lowerCode).orElseThrow().getId()).isEqualTo(lower.getId());
        assertThat(repository.findByCode(upperCode).orElseThrow().getId()).isEqualTo(upper.getId());
        assertThat(upper.getId()).isNotEqualTo(lower.getId());
    }

    @Test
    void rejectsDuplicateCodeUsingDatabaseUniqueConstraint() {
        String code = testCode();
        repository.saveAndFlush(new ShortLink(code, DESTINATION, CREATED_AT, null));
        entityManager.clear();

        DataIntegrityViolationException failure = catchThrowableOfType(
                DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(new ShortLink(code, DESTINATION, CREATED_AT, null)));

        assertThat(failure).isNotNull();
        assertThat(failure.getCause()).isInstanceOf(ConstraintViolationException.class);
        ConstraintViolationException constraintFailure = (ConstraintViolationException) failure.getCause();
        assertThat(constraintFailure.getConstraintName()).isEqualTo("uq_short_link_code");
        SQLException sqlFailure = constraintFailure.getSQLException();
        assertThat(sqlFailure.getSQLState()).isEqualTo("23505");
        // PostgreSQL has aborted this transaction; let the test framework roll it back.
    }

    private static String testCode() {
        return "test" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
    }
}
