package io.github.sputter9810.shortstack.api;

import java.net.URI;

import io.github.sputter9810.shortstack.service.LinkService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class LinkControllerTests {
    @Test
    void creationReturns201LocationAndDto() throws Exception {
        LinkService service = mock(LinkService.class);
        when(service.create("https://example.org"))
                .thenReturn(new LinkMetadata("AbCd1234", "https://sho.rt/AbCd1234", "https://example.org",
                        "2026-11-25T00:00:00.000000Z", null, false));
        when(service.metadataLocation("AbCd1234")).thenReturn(URI.create("https://sho.rt/api/links/AbCd1234"));
        MockMvcBuilders.standaloneSetup(new LinkController(service)).build()
                .perform(post("/api/links").contentType("application/json").content("{\"destinationUrl\":\"https://example.org\"}"))
                .andExpect(status().isCreated()).andExpect(header().string("Location", "https://sho.rt/api/links/AbCd1234"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value("AbCd1234"))
                .andExpect(jsonPath("$.createdAt").value("2026-11-25T00:00:00.000000Z"))
                .andExpect(jsonPath("$.expired").value(false)).andExpect(jsonPath("$.id").doesNotExist());
    }
}
