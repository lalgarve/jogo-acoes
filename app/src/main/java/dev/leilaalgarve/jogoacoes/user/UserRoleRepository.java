package dev.leilaalgarve.jogoacoes.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {

    List<UserRole> findByUser_Id(Long userId);

    boolean existsByRole_Name(String roleName);
}
