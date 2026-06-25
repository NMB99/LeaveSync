package com.leavesync.repository;

import com.leavesync.entity.User;
import com.leavesync.enums.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<User> findByInviteToken(String inviteToken);

    List<User> findByIsActiveTrue();

    List<User> findAllByRoleAndIsActiveTrue(Role role);

    Optional<User> findByIdAndIsActiveTrue(UUID teamId);

    boolean existsByTeamId(UUID teamId);

    @Query("SELECT u.id FROM User u WHERE u.teamId IN :teamIds")
    List<UUID> findIdsByTeamIdIn(List<UUID> teamIds);

    Page<User> findByTeamIdIn(List<UUID> teamIds, Pageable pageable);
}
