package dev.leilaalgarve.jogoacoes.login;

import dev.leilaalgarve.jogoacoes.login.UserRole;
import dev.leilaalgarve.jogoacoes.login.UserRoleId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {

    List<UserRole> findByUser_Id(Long userId);
}
