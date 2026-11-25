package io.github.sputter9810.shortstack.service;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoUnit;

import io.github.sputter9810.shortstack.api.LinkMetadata;
import io.github.sputter9810.shortstack.persistence.ShortLink;
import io.github.sputter9810.shortstack.persistence.ShortLinkRepository;
import io.github.sputter9810.shortstack.persistence.GeneratedLinkInserter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LinkService {
    private static final DateTimeFormatter TIME = new DateTimeFormatterBuilder().appendInstant(6).toFormatter();
    private final GeneratedLinkService generated;
    private final ShortLinkRepository repository;
    private final GeneratedLinkInserter inserter;
    private final Clock clock;
    private final String baseUrl;

    public LinkService(GeneratedLinkService generated, ShortLinkRepository repository, GeneratedLinkInserter inserter, Clock clock,
                       @Value("${shortstack.base-url}") String baseUrl) {
        this.generated = generated;
        this.repository = repository;
        this.inserter = inserter;
        this.clock = clock;
        URI base = URI.create(baseUrl);
        if (base.getHost() == null || !("http".equals(base.getScheme()) || "https".equals(base.getScheme()))
                || base.getRawUserInfo() != null || !base.getRawPath().isEmpty()
                || base.getRawQuery() != null || base.getRawFragment() != null) {
            throw new IllegalArgumentException("shortstack.base-url must be an absolute HTTP(S) origin");
        }
        this.baseUrl = baseUrl;
    }

    public LinkMetadata create(String destinationUrl) {
        return create(destinationUrl, null);
    }

    public LinkMetadata create(String destinationUrl, String alias) {
        return create(destinationUrl, alias, null);
    }

    public LinkMetadata create(String destinationUrl, String alias, String expiration) {
        DestinationRules.validate(destinationUrl);
        Instant createdAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        Instant expiresAt = parseExpiration(expiration, createdAt);
        if (alias == null) {
            return metadata(generated.create(destinationUrl, createdAt, expiresAt), false);
        }
        CodeRules.validateAlias(alias);
        try {
            return metadata(inserter.insert(alias, destinationUrl, createdAt, expiresAt), false);
        } catch (DataIntegrityViolationException failure) {
            if (GeneratedLinkService.isCodeCollision(failure)) {
                throw new AliasConflictException(failure);
            }
            throw failure;
        }
    }

    public URI metadataLocation(String code) {
        return URI.create(baseUrl + "/api/links/" + code);
    }

    @Transactional(readOnly = true)
    public LinkMetadata get(String code) {
        CodeRules.validateLookup(code);
        ShortLink link = repository.findByCode(code).orElseThrow(LinkNotFoundException::new);
        return metadata(link, expired(link));
    }

    @Transactional(readOnly = true)
    public String resolve(String code) {
        if (CodeRules.reserved(code)) {
            throw new InvalidCodeException(true);
        }
        CodeRules.validateLookup(code);
        ShortLink link = repository.findByCode(code).orElseThrow(LinkNotFoundException::new);
        if (expired(link)) {
            throw new LinkExpiredException();
        }
        return link.getDestinationUrl();
    }

    @Transactional
    public void delete(String code) {
        CodeRules.validateLookup(code);
        if (repository.deleteByCode(code) == 0) {
            throw new LinkNotFoundException();
        }
    }

    private boolean expired(ShortLink link) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        return link.getExpiresAt() != null && !now.isBefore(link.getExpiresAt());
    }

    private static Instant parseExpiration(String input, Instant createdAt) {
        if (input == null) {
            return null;
        }
        try {
            if (!input.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(\\.[0-9]{1,6})?(Z|[+-][0-9]{2}:[0-9]{2})")) {
                throw new IllegalArgumentException();
            }
            Instant expiration = OffsetDateTime.parse(input).toInstant();
            if (!expiration.isAfter(createdAt) || expiration.isBefore(Instant.parse("0001-01-01T00:00:00Z"))
                    || !expiration.isBefore(Instant.parse("+10000-01-01T00:00:00Z"))) {
                throw new IllegalArgumentException();
            }
            return expiration;
        } catch (IllegalArgumentException | java.time.DateTimeException invalid) {
            throw new InvalidLinkRequestException("expiresAt", "Must be a future RFC 3339 timestamp with an offset and at most six fractional digits.");
        }
    }

    private LinkMetadata metadata(ShortLink link, boolean expired) {
        return new LinkMetadata(link.getCode(), baseUrl + "/" + link.getCode(), link.getDestinationUrl(),
                TIME.format(link.getCreatedAt()), link.getExpiresAt() == null ? null : TIME.format(link.getExpiresAt()), expired);
    }
}
