package com.leavesync.repository;

import com.leavesync.entity.LeaveBalance;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LeaveBalanceRepository extends JpaRepository<LeaveBalance, UUID> {

    Optional<LeaveBalance> findByUserIdAndYear(UUID userId, int year);

    List<LeaveBalance> findByYear(int year);
    Page<LeaveBalance> findByYear(int year, Pageable pageable);

    List<LeaveBalance> findByUserIdInAndYear(List<UUID> userIds, int year);
    Page<LeaveBalance> findByUserIdInAndYear(List<UUID> userIds, int year, Pageable pageable);

}
