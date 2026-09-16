package dev.leilaalgarve.jogoacoes.repository;

import dev.leilaalgarve.jogoacoes.domain.UserRole;
import dev.leilaalgarve.jogoacoes.domain.UserRoleId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {

    List<UserRole> findByUser_Id(Long userId);
}
