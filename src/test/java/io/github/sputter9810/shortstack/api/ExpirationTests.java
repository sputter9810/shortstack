package io.github.sputter9810.shortstack.api;

import java.time.*;
import java.util.Optional;

import io.github.sputter9810.shortstack.persistence.*;
import io.github.sputter9810.shortstack.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ExpirationTests {
    private static final Instant NOW = Instant.parse("2026-11-25T00:00:00.123456Z");
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final ShortLinkRepository repository = mock(ShortLinkRepository.class);
    private final GeneratedLinkService generated = mock(GeneratedLinkService.class);
    private final LinkService service = new LinkService(generated, repository, mock(GeneratedLinkInserter.class), clock, "https://sho.rt");

    @Test
    void normalisesFutureOffsetAndPreservesMicroseconds() throws Exception {
        Instant expiry = Instant.parse("2026-11-26T00:00:00.654321Z");
        when(generated.create("https://example.org", NOW, expiry))
                .thenReturn(new ShortLink("AbCd1234", "https://example.org", NOW, expiry));
        MockMvcBuilders.standaloneSetup(new LinkController(service)).build()
                .perform(post("/api/links").contentType("application/json").content(
                        "{\"destinationUrl\":\"https://example.org\",\"expiresAt\":\"2026-11-26T11:00:00.654321+11:00\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.expiresAt").value("2026-11-26T00:00:00.654321Z"));
        verify(generated).create("https://example.org", NOW, expiry);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "2026-11-25T00:00:00.123456Z", "2026-11-24T00:00:00Z", "2026-11-26T00:00:00",
            "2026-11-26T00:00:00.1234567Z", "2026-11-26T00:00:60Z", "2026-02-30T00:00:00Z", "0000-12-31T00:00:00Z",
            "2027-01-01T00:00:00+24:00", "2027-01-01T00:00:00+00:60"})
    void rejectsInvalidOrNonFutureExpiration(String expiration) throws Exception {
        MockMvcBuilders.standaloneSetup(new LinkController(service)).setControllerAdvice(new ApiExceptionHandler(clock)).build()
                .perform(post("/api/links").contentType("application/json").content(
                        "{\"destinationUrl\":\"https://example.org\",\"expiresAt\":\"" + expiration + "\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.violations[0].field").value("expiresAt"));
        verifyNoInteractions(generated);
    }

    @Test
    void expiryBoundaryIsInclusiveAndMetadataRemainsAccessible() throws Exception {
        when(repository.findByCode("boundary")).thenReturn(Optional.of(new ShortLink("boundary", "https://example.org",
                NOW.minusSeconds(1), NOW)));
        assertThat(service.get("boundary").expired()).isTrue();
        assertThatThrownBy(() -> service.resolve("boundary")).isInstanceOf(LinkExpiredException.class);
        var mvc = MockMvcBuilders.standaloneSetup(new LinkController(service), new RedirectController(service))
                .setControllerAdvice(new ApiExceptionHandler(clock)).build();
        mvc.perform(get("/boundary")).andExpect(status().isGone()).andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.error").value("LINK_EXPIRED"));
        mvc.perform(get("/api/links/boundary")).andExpect(status().isOk()).andExpect(jsonPath("$.expired").value(true));
    }

    @Test
    void oneMicrosecondBeforeExpirationStillResolvesAndAfterIsExpired() {
        when(repository.findByCode("future1")).thenReturn(Optional.of(new ShortLink("future1", "https://example.org",
                NOW.minusSeconds(1), NOW.plusNanos(1000))));
        when(repository.findByCode("past123")).thenReturn(Optional.of(new ShortLink("past123", "https://example.org",
                NOW.minusSeconds(1), NOW.minusNanos(1000))));
        assertThat(service.resolve("future1")).isEqualTo("https://example.org");
        assertThat(service.get("future1").expired()).isFalse();
        assertThatThrownBy(() -> service.resolve("past123")).isInstanceOf(LinkExpiredException.class);
    }

    @ParameterizedTest
    @CsvSource({"2027-01-02T00:00:00+23:59,2027-01-01T00:01:00Z",
            "2027-01-01T00:00:00-23:59,2027-01-01T23:59:00Z",
            "2027-01-01t00:00:00z,2027-01-01T00:00:00Z"})
    void acceptsTheFullApprovedRfc3339OffsetSyntax(String input, String utc) {
        Instant expiry = Instant.parse(utc);
        when(generated.create("https://example.org", NOW, expiry))
                .thenReturn(new ShortLink("AbCd1234", "https://example.org", NOW, expiry));
        assertThat(service.create("https://example.org", null, input).expiresAt())
                .isEqualTo(new java.time.format.DateTimeFormatterBuilder().appendInstant(6).toFormatter().format(expiry));
    }
}
