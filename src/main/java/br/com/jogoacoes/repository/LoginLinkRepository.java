package br.com.jogoacoes.repository;

import br.com.jogoacoes.domain.LoginLink;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoginLinkRepository extends JpaRepository<LoginLink, Long> {
}
