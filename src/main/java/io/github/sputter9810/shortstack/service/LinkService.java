package io.github.sputter9810.shortstack.service;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
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
        if (destinationUrl == null || destinationUrl.length() > 2048) {
            throw new IllegalArgumentException("Invalid destination URL");
        }
        URI destination = URI.create(destinationUrl);
        if (destination.getHost() == null || !("http".equalsIgnoreCase(destination.getScheme())
                || "https".equalsIgnoreCase(destination.getScheme()))) {
            throw new IllegalArgumentException("Invalid destination URL");
        }
        Instant createdAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        if (alias == null) {
            return metadata(generated.create(destinationUrl, createdAt, null), false);
        }
        CodeRules.validateAlias(alias);
        try {
            return metadata(inserter.insert(alias, destinationUrl, createdAt, null), false);
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
        return metadata(repository.findByCode(code).orElseThrow(LinkNotFoundException::new), false);
    }

    @Transactional(readOnly = true)
    public String resolve(String code) {
        return repository.findByCode(code).orElseThrow(LinkNotFoundException::new).getDestinationUrl();
    }

    private LinkMetadata metadata(ShortLink link, boolean expired) {
        return new LinkMetadata(link.getCode(), baseUrl + "/" + link.getCode(), link.getDestinationUrl(),
                TIME.format(link.getCreatedAt()), link.getExpiresAt() == null ? null : TIME.format(link.getExpiresAt()), expired);
    }
}
