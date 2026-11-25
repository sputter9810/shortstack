package io.github.sputter9810.shortstack.api;

import io.github.sputter9810.shortstack.service.LinkService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/links")
public class LinkController {
    private final LinkService links;

    public LinkController(LinkService links) {
        this.links = links;
    }

    @PostMapping(consumes = "application/json", produces = "application/json")
    public ResponseEntity<LinkMetadata> create(@RequestBody CreateLinkRequest request) {
        LinkMetadata result = links.create(request.destinationUrl(), request.customAlias(), request.expiresAt());
        return ResponseEntity.created(links.metadataLocation(result.code())).cacheControl(CacheControl.noStore()).body(result);
    }

    @GetMapping(value = "/{code}", produces = "application/json")
    public ResponseEntity<LinkMetadata> get(@PathVariable String code) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(links.get(code));
    }

    @DeleteMapping("/{code}")
    public ResponseEntity<Void> delete(@PathVariable String code) {
        links.delete(code);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
