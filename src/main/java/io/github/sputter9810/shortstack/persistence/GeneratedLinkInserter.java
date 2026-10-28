package io.github.sputter9810.shortstack.persistence;

import java.time.Instant;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class GeneratedLinkInserter {

    private final ShortLinkRepository repository;

    public GeneratedLinkInserter(ShortLinkRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ShortLink insert(String code, String destinationUrl, Instant createdAt, Instant expiresAt) {
        return repository.saveAndFlush(new ShortLink(code, destinationUrl, createdAt, expiresAt));
    }
}
