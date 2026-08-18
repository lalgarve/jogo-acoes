package io.deployo.jogoacoes.repository;

import io.deployo.jogoacoes.domain.UserRole;
import io.deployo.jogoacoes.domain.UserRoleId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {
}
