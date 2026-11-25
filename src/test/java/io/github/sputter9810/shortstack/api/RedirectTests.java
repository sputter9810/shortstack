package io.github.sputter9810.shortstack.api;

import java.time.Clock;
import java.time.Instant;
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

class RedirectTests {
    @Test
    void redirectsVerbatimAndIgnoresIncomingQuery() throws Exception {
        ShortLinkRepository repository = mock(ShortLinkRepository.class);
        String destination = "https://example.org/a?original=1#part";
        when(repository.findByCode("AbCd1234")).thenReturn(Optional.of(
                new ShortLink("AbCd1234", destination, Instant.parse("2026-01-01T00:00:00Z"), null)));
        LinkService service = new LinkService(mock(GeneratedLinkService.class), repository, Clock.systemUTC(), "https://sho.rt");
        assertThat(service.resolve("AbCd1234")).isEqualTo(destination);
        MockMvcBuilders.standaloneSetup(new RedirectController(service)).build()
                .perform(get("/AbCd1234?incoming=ignored")).andExpect(status().isFound())
                .andExpect(header().string("Location", destination))
                .andExpect(header().string("Cache-Control", "no-store")).andExpect(content().string(""));
    }

    @Test
    void missingCodeDoesNotRedirect() throws Exception {
        LinkService service = mock(LinkService.class);
        when(service.resolve("missing-code")).thenThrow(new LinkNotFoundException());
        MockMvcBuilders.standaloneSetup(new RedirectController(service))
                .setControllerAdvice(new ApiExceptionHandler(Clock.systemUTC())).build()
                .perform(get("/missing-code")).andExpect(status().isNotFound())
                .andExpect(header().doesNotExist("Location")).andExpect(jsonPath("$.error").value("LINK_NOT_FOUND"));
    }
}
