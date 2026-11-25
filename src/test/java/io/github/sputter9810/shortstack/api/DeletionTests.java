package io.github.sputter9810.shortstack.api;

import java.time.Clock;
import io.github.sputter9810.shortstack.persistence.*;
import io.github.sputter9810.shortstack.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DeletionTests {
    @Test
    void usesAtomicDeleteCountWithoutPriorLookup() throws Exception {
        ShortLinkRepository repository = mock(ShortLinkRepository.class);
        when(repository.deleteByCode("delete-me")).thenReturn(1);
        LinkService service = new LinkService(mock(GeneratedLinkService.class), repository, mock(GeneratedLinkInserter.class),
                Clock.systemUTC(), "https://sho.rt");
        MockMvcBuilders.standaloneSetup(new LinkController(service)).build()
                .perform(delete("/api/links/delete-me")).andExpect(status().isNoContent())
                .andExpect(content().string("")).andExpect(header().string("Cache-Control", "no-store"));
        verify(repository).deleteByCode("delete-me");
        verifyNoMoreInteractions(repository);
    }

    @Test
    void absentAndRepeatedDeleteReturn404() throws Exception {
        ShortLinkRepository repository = mock(ShortLinkRepository.class);
        when(repository.deleteByCode("delete-me")).thenReturn(1, 0);
        LinkService service = new LinkService(mock(GeneratedLinkService.class), repository, mock(GeneratedLinkInserter.class),
                Clock.systemUTC(), "https://sho.rt");
        var mvc = MockMvcBuilders.standaloneSetup(new LinkController(service))
                .setControllerAdvice(new ApiExceptionHandler(Clock.systemUTC())).build();
        mvc.perform(delete("/api/links/delete-me")).andExpect(status().isNoContent());
        mvc.perform(delete("/api/links/delete-me")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("LINK_NOT_FOUND"));
    }
}
