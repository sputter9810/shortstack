package io.github.sputter9810.shortstack.service;

import java.time.Instant;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import io.github.sputter9810.shortstack.persistence.GeneratedLinkInserter;
import io.github.sputter9810.shortstack.persistence.ShortLink;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GeneratedLinkService {

    private static final int MAX_INSERT_ATTEMPTS = 5;

    private final ShortCodeGenerator generator;
    private final GeneratedLinkInserter inserter;

    public GeneratedLinkService(ShortCodeGenerator generator, GeneratedLinkInserter inserter) {
        this.generator = generator;
        this.inserter = inserter;
    }

    // A caller must not wrap retries in a shared transaction. The inserter proxy
    // completes each commit/rollback before control returns to this coordinator.
    @Transactional(propagation = Propagation.NEVER)
    public ShortLink create(String destinationUrl, Instant createdAt, Instant expiresAt) {
        for (int attempt = 1; attempt <= MAX_INSERT_ATTEMPTS; attempt++) {
            String code = generator.generate();
            try {
                return inserter.insert(code, destinationUrl, createdAt, expiresAt);
            } catch (DataIntegrityViolationException failure) {
                if (!isCodeCollision(failure)) {
                    throw failure;
                }
                if (attempt == MAX_INSERT_ATTEMPTS) {
                    throw new CodeGenerationExhaustedException(failure);
                }
            }
        }
        throw new IllegalStateException("Insertion attempt limit must be positive.");
    }

    private static boolean isCodeCollision(Throwable failure) {
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable cause = failure; cause != null && visited.add(cause); cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && "uq_short_link_code".equals(violation.getConstraintName())
                    && violation.getSQLException() != null
                    && "23505".equals(violation.getSQLException().getSQLState())) {
                return true;
            }
        }
        return false;
    }
}
