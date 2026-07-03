package com.leavesync.repository;

import com.leavesync.entity.Team;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TeamRepository extends JpaRepository<Team, UUID> {

    List<Team> findByManagerId(UUID managerId);
    Page<Team> findByManagerId(UUID managerId, Pageable pageable);

    boolean existsByManagerId(UUID managerId);

    boolean existsByName(String name);
}
