package io.github.sputter9810.shortstack.api;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import io.github.sputter9810.shortstack.persistence.ShortLink;
import io.github.sputter9810.shortstack.persistence.ShortLinkRepository;
import io.github.sputter9810.shortstack.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MetadataTests {
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-25T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void returnsStoredMetadataWithoutRedirectOrEntityId() throws Exception {
        ShortLinkRepository repository = mock(ShortLinkRepository.class);
        when(repository.findByCode("AbCd1234")).thenReturn(Optional.of(
                new ShortLink("AbCd1234", "https://example.org", clock.instant(), null)));
        LinkService service = new LinkService(mock(GeneratedLinkService.class), repository, mock(io.github.sputter9810.shortstack.persistence.GeneratedLinkInserter.class), clock, "https://sho.rt");
        assertThat(service.get("AbCd1234").destinationUrl()).isEqualTo("https://example.org");
        MockMvcBuilders.standaloneSetup(new LinkController(service)).build()
                .perform(get("/api/links/AbCd1234")).andExpect(status().isOk())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.shortUrl").value("https://sho.rt/AbCd1234"))
                .andExpect(jsonPath("$.id").doesNotExist());
    }

    @Test
    void missingMetadataReturnsStructured404() throws Exception {
        ShortLinkRepository repository = mock(ShortLinkRepository.class);
        when(repository.findByCode("missing-code")).thenReturn(Optional.empty());
        LinkService service = new LinkService(mock(GeneratedLinkService.class), repository, mock(io.github.sputter9810.shortstack.persistence.GeneratedLinkInserter.class), clock, "https://sho.rt");
        MockMvcBuilders.standaloneSetup(new LinkController(service)).setControllerAdvice(new ApiExceptionHandler(clock)).build()
                .perform(get("/api/links/missing-code")).andExpect(status().isNotFound())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.error").value("LINK_NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/api/links/missing-code"))
                .andExpect(jsonPath("$.violations").isEmpty());
    }
}
