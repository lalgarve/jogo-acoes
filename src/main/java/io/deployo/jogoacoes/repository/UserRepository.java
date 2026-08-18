package io.deployo.jogoacoes.repository;

import io.deployo.jogoacoes.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
}
