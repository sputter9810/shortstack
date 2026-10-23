package io.github.sputter9810.shortstack.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "short_link")
public class ShortLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, length = 32)
    private String code;

    @Column(name = "destination_url", nullable = false, length = 2048)
    private String destinationUrl;

    @Column(name = "created_at", nullable = false, secondPrecision = 6)
    private Instant createdAt;

    @Column(name = "expires_at", secondPrecision = 6)
    private Instant expiresAt;

    protected ShortLink() {
    }

    public ShortLink(String code, String destinationUrl, Instant createdAt, Instant expiresAt) {
        this.code = code;
        this.destinationUrl = destinationUrl;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getDestinationUrl() {
        return destinationUrl;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
