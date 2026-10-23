package io.github.sputter9810.shortstack.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ShortLinkRepository extends JpaRepository<ShortLink, Long> {

    Optional<ShortLink> findByCode(String code);
}
