package io.github.sputter9810.shortstack.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import io.github.sputter9810.shortstack.persistence.ShortLink;
import io.github.sputter9810.shortstack.persistence.ShortLinkRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LinkServiceTests {
    @Test
    void creationDelegatesToGeneratedPersistenceAndReturnsMetadata() {
        Instant now = Instant.parse("2026-11-25T00:00:00.123456Z");
        GeneratedLinkService generated = mock(GeneratedLinkService.class);
        when(generated.create("https://example.org/a?b=1#c", now, null))
                .thenReturn(new ShortLink("AbCd1234", "https://example.org/a?b=1#c", now, null));
        LinkService service = new LinkService(generated, mock(ShortLinkRepository.class),
                Clock.fixed(now.plusNanos(789), ZoneOffset.UTC), "https://sho.rt");

        var result = service.create("https://example.org/a?b=1#c");

        assertThat(result.code()).isEqualTo("AbCd1234");
        assertThat(result.shortUrl()).isEqualTo("https://sho.rt/AbCd1234");
        assertThat(result.destinationUrl()).isEqualTo("https://example.org/a?b=1#c");
        assertThat(result.createdAt()).isEqualTo("2026-11-25T00:00:00.123456Z");
        assertThat(result.expiresAt()).isNull();
        assertThat(result.expired()).isFalse();
        verify(generated).create("https://example.org/a?b=1#c", now, null);
    }
}
