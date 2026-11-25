package io.github.sputter9810.shortstack.api;

import java.time.Clock;
import java.util.stream.Stream;
import io.github.sputter9810.shortstack.service.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class FailureMappingTests {
    @ParameterizedTest
    @MethodSource("failures")
    void mapsFailuresToSanitisedEnvelope(RuntimeException failure, int status, String code) throws Exception {
        LinkService service = mock(LinkService.class);
        when(service.create("https://example.org", null, null)).thenThrow(failure);
        MockMvcBuilders.standaloneSetup(new LinkController(service)).setControllerAdvice(new ApiExceptionHandler(Clock.systemUTC())).build()
                .perform(post("/api/links?secret=query").contentType("application/json")
                        .content("{\"destinationUrl\":\"https://example.org\"}"))
                .andExpect(status().is(status)).andExpect(jsonPath("$.error").value(code))
                .andExpect(jsonPath("$.path").value("/api/links")).andExpect(header().doesNotExist("Location"))
                .andExpect(content().string(not(containsString("secret"))));
    }

    private static Stream<Arguments> failures() {
        return Stream.of(
                Arguments.of(new CodeGenerationExhaustedException(new RuntimeException("secret")), 503, "CODE_GENERATION_EXHAUSTED"),
                Arguments.of(new DataAccessResourceFailureException("secret"), 503, "SERVICE_UNAVAILABLE"),
                Arguments.of(new DataIntegrityViolationException("secret"), 500, "INTERNAL_ERROR"),
                Arguments.of(new IllegalStateException("secret"), 500, "INTERNAL_ERROR"));
    }
}
