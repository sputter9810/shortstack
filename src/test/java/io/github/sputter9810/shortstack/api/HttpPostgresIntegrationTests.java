package io.github.sputter9810.shortstack.api;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import io.github.sputter9810.shortstack.persistence.GeneratedLinkInserter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "shortstack.base-url=https://sho.rt")
class HttpPostgresIntegrationTests {
    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private GeneratedLinkInserter inserter;
    private final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final String destination = "https://example.org/http-test/" + UUID.randomUUID() + "?query=1#fragment";

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM short_link WHERE destination_url = ?", destination);
    }

    @Test
    void generatedCreationSurvivesAcrossHttpRequestsThenDeletionRemovesReads() throws Exception {
        var response = send("POST", "/api/links", Map.of("destinationUrl", destination));
        assertThat(response.statusCode()).isEqualTo(201);
        LinkMetadata created = mapper.readValue(response.body(), LinkMetadata.class);
        assertThat(created.code()).matches("[A-Za-z0-9]{8}");
        assertThat(created.shortUrl()).isEqualTo("https://sho.rt/" + created.code());
        assertThat(response.headers().firstValue("Location")).hasValue("https://sho.rt/api/links/" + created.code());
        assertThat(jdbc.queryForObject("SELECT destination_url FROM short_link WHERE code=?", String.class, created.code())).isEqualTo(destination);
        var metadata = send("GET", "/api/links/" + created.code(), null);
        assertThat(metadata.statusCode()).isEqualTo(200);
        assertThat(mapper.readValue(metadata.body(), LinkMetadata.class)).isEqualTo(created);
        var redirect = send("GET", "/" + created.code() + "?ignored=2", null);
        assertThat(redirect.statusCode()).isEqualTo(302);
        assertThat(redirect.headers().firstValue("Location")).hasValue(destination);
        assertThat(redirect.body()).isEmpty();
        assertThat(send("DELETE", "/api/links/" + created.code(), null).statusCode()).isEqualTo(204);
        assertThat(send("GET", "/api/links/" + created.code(), null).statusCode()).isEqualTo(404);
        assertThat(send("GET", "/" + created.code(), null).statusCode()).isEqualTo(404);
        assertThat(send("DELETE", "/api/links/" + created.code(), null).statusCode()).isEqualTo(404);
    }

    @Test
    void aliasSharesUniquenessAndExpirationRoundTripsThroughDatabase() throws Exception {
        String alias = UUID.randomUUID().toString().replace("-", "");
        String expiration = "9999-01-01T01:00:00.654321+01:00";
        var body = Map.of("destinationUrl", destination, "customAlias", alias, "expiresAt", expiration);
        var response = send("POST", "/api/links", body);
        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(mapper.readValue(response.body(), LinkMetadata.class).expiresAt()).isEqualTo("9999-01-01T00:00:00.654321Z");
        assertThat(send("POST", "/api/links", body).statusCode()).isEqualTo(409);
        assertThat(send("GET", "/" + alias, null).statusCode()).isEqualTo(302);
        assertThat(send("DELETE", "/api/links/" + alias, null).statusCode()).isEqualTo(204);
        assertThat(send("POST", "/api/links", body).statusCode()).isEqualTo(201);
    }

    @Test
    void expiredRowsRemainInspectableButNeverRedirect() throws Exception {
        String code = UUID.randomUUID().toString().replace("-", "");
        inserter.insert(code, destination, Instant.parse("2020-01-01T00:00:00Z"), Instant.parse("2020-01-02T00:00:00.123456Z"));
        var redirect = send("GET", "/" + code, null);
        assertThat(redirect.statusCode()).isEqualTo(410);
        assertThat(redirect.headers().firstValue("Location")).isEmpty();
        assertThat(mapper.readValue(redirect.body(), ApiError.class).error()).isEqualTo("LINK_EXPIRED");
        var metadata = send("GET", "/api/links/" + code, null);
        assertThat(metadata.statusCode()).isEqualTo(200);
        assertThat(mapper.readValue(metadata.body(), LinkMetadata.class).expired()).isTrue();
        assertThat(send("DELETE", "/api/links/" + code, null).statusCode()).isEqualTo(204);
    }

    private HttpResponse<String> send(String method, String path, Map<String, String> body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (body != null) {
            request.header("Content-Type", "application/json").method(method,
                    HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
        } else {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        }
        var response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.headers().firstValue("Cache-Control")).hasValue("no-store");
        return response;
    }
}
