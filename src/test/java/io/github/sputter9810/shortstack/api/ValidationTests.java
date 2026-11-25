package io.github.sputter9810.shortstack.api;

import java.util.Collections;
import java.util.Map;
import java.util.stream.Stream;

import io.github.sputter9810.shortstack.service.DestinationRules;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class ValidationTests {
    @Autowired private WebApplicationContext context;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(new NoStoreFilter()).build();
    }

    @ParameterizedTest
    @MethodSource("invalidUrls")
    void rejectsUnsupportedOrMalformedDestinations(String url) throws Exception {
        String body = JsonMapper.builder().build().writeValueAsString(Collections.singletonMap("destinationUrl", url));
        mvc.perform(post("/api/links").contentType("application/json").content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.violations[0].field").value("destinationUrl"))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    private static Stream<String> invalidUrls() {
        return Stream.of(null, "", "/relative", "ftp://example.org", "javascript:alert(1)", "http://",
                "https://user:pass@example.org", "https://example.org/a b", " https://example.org",
                "https://example.org/\n", "https://example.org/\\path", "https://example.org/é",
                "https://éxample.org", "https://example.org:0", "https://example.org:65536",
                "https://example.org:", "https://example.org:-1", "https://example.org/%xx",
                "https://example.org/" + "a".repeat(2048));
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://example.org/a?b=1#c", "HTTP://example.org", "http://localhost:8080",
            "http://127.0.0.1", "https://[::1]:65535/a", "https://xn--xample-9ua.org/%C3%A9", "https://example.org:1"})
    void acceptsApprovedSyntacticFormsWithoutFetching(String url) {
        assertThatCode(() -> DestinationRules.validate(url)).doesNotThrowAnyException();
    }

    @Test
    void acceptsExactly2048AsciiCharacters() {
        String prefix = "https://example.org/";
        assertThatCode(() -> DestinationRules.validate(prefix + "a".repeat(2048 - prefix.length()))).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "[]", "null", "1", "\"text\"", "{\"destinationUrl\":12}",
            "{\"destinationUrl\":true}", "{\"destinationUrl\":[]}", "{\"destinationUrl\":{}}",
            "{\"destinationUrl\":\"https://example.org\",\"unknown\":1}",
            "{\"destinationUrl\":\"https://example.org\",\"customAlias\":false}",
            "{\"destinationUrl\":\"https://example.org\",\"expiresAt\":123}",
            "{\"destinationUrl\":\"https://example.org\"} {}"})
    void rejectsWrongJsonShapesTypesAndUnknownProperties(String body) throws Exception {
        mvc.perform(post("/api/links").contentType("application/json").content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.violations").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api", "/ACTUATOR", "/robots.txt", "/v3", "/error", "/api/not/a/route"})
    void reservedAndUnmatchedRoutesReturnStructured404(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("ROUTE_NOT_FOUND"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/aa", "/api/links/aa", "/invalid!"})
    void invalidMatchedCodesReturn400(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("INVALID_CODE"));
    }

    @Test
    void mapsFrameworkFailuresWithoutDetailsAndPreservesAllow() throws Exception {
        mvc.perform(put("/api/links/unknown")).andExpect(status().isMethodNotAllowed())
                .andExpect(header().exists("Allow")).andExpect(jsonPath("$.error").value("METHOD_NOT_ALLOWED"));
        mvc.perform(post("/api/links").contentType("text/plain").content("x")).andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error").value("UNSUPPORTED_MEDIA_TYPE"));
        mvc.perform(get("/api/links/unknown").accept("text/plain")).andExpect(status().isNotAcceptable())
                .andExpect(jsonPath("$.error").value("NOT_ACCEPTABLE"));
    }
}
