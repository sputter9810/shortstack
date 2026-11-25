package io.github.sputter9810.shortstack.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShortLinkRepository extends JpaRepository<ShortLink, Long> {

    Optional<ShortLink> findByCode(String code);

    @Modifying
    @Query("delete from ShortLink link where link.code = :code")
    int deleteByCode(@Param("code") String code);
}
