package br.com.jogoacoes.repository;

import br.com.jogoacoes.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, Long> {
}
