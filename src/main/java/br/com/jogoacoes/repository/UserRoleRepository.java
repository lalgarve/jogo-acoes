package br.com.jogoacoes.repository;

import br.com.jogoacoes.domain.UserRole;
import br.com.jogoacoes.domain.UserRoleId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {
}
