package io.github.sputter9810.shortstack.api;

import java.net.URI;

import io.github.sputter9810.shortstack.service.LinkService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RedirectController {
    private final LinkService links;

    public RedirectController(LinkService links) {
        this.links = links;
    }

    @GetMapping("/{code}")
    public ResponseEntity<Void> resolve(@PathVariable String code) {
        return ResponseEntity.status(302).location(URI.create(links.resolve(code)))
                .cacheControl(CacheControl.noStore()).build();
    }
}
