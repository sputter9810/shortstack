package io.github.sputter9810.shortstack.api;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import io.github.sputter9810.shortstack.persistence.*;
import io.github.sputter9810.shortstack.service.*;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AliasTests {
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-25T00:00:00Z"), ZoneOffset.UTC);
    private final GeneratedLinkService generated = mock(GeneratedLinkService.class);
    private final GeneratedLinkInserter inserter = mock(GeneratedLinkInserter.class);
    private final LinkService service = new LinkService(generated, mock(ShortLinkRepository.class), inserter, clock, "https://sho.rt");

    private MockMvc mvc() {
        return MockMvcBuilders.standaloneSetup(new LinkController(service))
                .setControllerAdvice(new ApiExceptionHandler(clock)).build();
    }

    @Test
    void createsCustomAliasWithoutGenerating() throws Exception {
        when(inserter.insert("Launch_1", "https://example.org", clock.instant(), null))
                .thenReturn(new ShortLink("Launch_1", "https://example.org", clock.instant(), null));
        mvc().perform(post("/api/links").contentType("application/json")
                        .content("{\"destinationUrl\":\"https://example.org\",\"customAlias\":\"Launch_1\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.code").value("Launch_1"));
        verifyNoInteractions(generated);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "ab", "a!b", "a b", "API", "actuator", "swagger-ui", "abcdefghijklmnopqrstuvwxyz1234567"})
    void rejectsInvalidOrReservedAliases(String alias) throws Exception {
        mvc().perform(post("/api/links").contentType("application/json")
                        .content("{\"destinationUrl\":\"https://example.org\",\"customAlias\":\"" + alias + "\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.violations[0].field").value("customAlias"));
        verifyNoInteractions(inserter, generated);
    }

    @Test
    void conflictsDoNotRetryOrLeakDatabaseDetails() throws Exception {
        when(inserter.insert("Launch_1", "https://example.org", clock.instant(), null)).thenThrow(
                new DataIntegrityViolationException("database internals", new ConstraintViolationException("duplicate",
                        new SQLException("duplicate", "23505"), "uq_short_link_code")));
        mvc().perform(post("/api/links").contentType("application/json")
                        .content("{\"destinationUrl\":\"https://example.org\",\"customAlias\":\"Launch_1\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("ALIAS_CONFLICT"))
                .andExpect(jsonPath("$.message").value("The custom alias is already in use."));
        verify(inserter).insert("Launch_1", "https://example.org", clock.instant(), null);
        verifyNoMoreInteractions(inserter);
        verifyNoInteractions(generated);
    }
}
