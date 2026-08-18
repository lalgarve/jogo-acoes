package io.deployo.jogoacoes.repository;

import io.deployo.jogoacoes.domain.LoginLink;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoginLinkRepository extends JpaRepository<LoginLink, Long> {
}
