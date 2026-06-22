package com.leavesync.repository;

import com.leavesync.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    @Query("SELECT a FROM AuditLog a WHERE a.leaveRequestId IN :requestIds" +
            " AND (CAST(:startDate AS LOCALDATETIME) IS NULL OR a.changedAt >= :startDate)" +
            " AND (CAST(:endDate AS LOCALDATETIME) IS NULL OR a.changedAt <= :endDate)")
    List<AuditLog> findByLeaveRequestIdInAndDateRange(
            @Param("requestIds") List<UUID> requestIds,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

}
