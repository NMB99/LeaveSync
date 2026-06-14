package com.leavesync.repository;

import com.leavesync.entity.User;
import com.leavesync.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<User> findByInviteToken(String inviteToken);

    List<User> findByTeamId(UUID teamId);

    List<User> findByIsActiveTrue();

    Optional<User> findFirstByRoleAndIsActiveTrue(Role role);

    boolean existsByTeamId(UUID teamId);

    List<User> findByTeamIdIn(List<UUID> teamIds);
}
