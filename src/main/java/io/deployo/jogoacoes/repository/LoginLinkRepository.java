package io.deployo.jogoacoes.repository;

import io.deployo.jogoacoes.domain.LoginLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LoginLinkRepository extends JpaRepository<LoginLink, Long> {

    Optional<LoginLink> findByToken(String token);

    List<LoginLink> findByUser_IdAndUsedAtIsNullAndInvalidatedAtIsNull(Long userId);

    Optional<LoginLink> findFirstByUser_IdAndUsedAtIsNullAndInvalidatedAtIsNullOrderByIdDesc(Long userId);
}
