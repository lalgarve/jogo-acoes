package br.com.jogoacoes.repository;

import br.com.jogoacoes.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
}
