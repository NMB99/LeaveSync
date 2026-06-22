package com.leavesync.repository;

import com.leavesync.entity.LeaveRequest;
import com.leavesync.enums.LeaveStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, UUID> {
    
    List<LeaveRequest> findByUserId(UUID userId);
    Page<LeaveRequest> findByUserId(UUID userId, Pageable pageable);

    List<LeaveRequest> findByUserIdIn(List<UUID> userIds);
    Page<LeaveRequest> findByUserIdIn(List<UUID> userIds, Pageable pageable);

    List<LeaveRequest> findByUserIdAndStatus(UUID userId, LeaveStatus status);

    List<LeaveRequest> findByUserIdAndStatusAndEndDateGreaterThanEqual(UUID userId, LeaveStatus status, LocalDate endDate);

    @Query("SELECT r FROM LeaveRequest r WHERE r.userId = :userId" +
            " AND r.status IN :statuses" +
            " AND r.startDate <= :endDate" +
            " AND r.endDate >= :startDate")
    List<LeaveRequest> findOverlappingRequests(
            @Param("userId") UUID userID,
            @Param("statuses") List<LeaveStatus> statuses,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("SELECT r FROM LeaveRequest r WHERE r.status = :status"  +
            " AND r.escalationStartDate = :date")
    List<LeaveRequest> findByStatusAndEscalationStartDate(
            @Param("status") LeaveStatus status,
            @Param("date") LocalDate date
    );

    List<LeaveRequest> findByStatusInAndStartDate(List<LeaveStatus> status, LocalDate startDate);

    @Query("SELECT r FROM LeaveRequest r WHERE r.status IN :statuses" +
            " AND r.startDate <= :date AND r.endDate >= :date")
    List<LeaveRequest> findByStatusInAndDateWithin(
            @Param("statuses") List<LeaveStatus> statuses,
            @Param("date") LocalDate date
    );

    @Query("SELECT r FROM LeaveRequest r WHERE r.status IN :statuses" +
            " AND r.startDate <= :date AND r.endDate >= :date" +
            " AND r.userId IN :userIds")
    List<LeaveRequest> findByStatusInAndDateWithinAndUserIdIn(
            @Param("statuses") List<LeaveStatus> statuses,
            @Param("date") LocalDate date,
            @Param("userIds") List<UUID> userIds
    );

    @Query("SELECT r FROM LeaveRequest r WHERE r.leaveTypeId = :leaveTypeId" +
            " AND r.status IN :statuses" +
            " AND (CAST(:startDate AS LOCALDATE) IS NULL OR r.startDate >= :startDate)" +
            " AND (CAST(:endDate AS LOCALDATE) IS NULL OR r.startDate <= :endDate)")
    List<LeaveRequest> findByLeaveTypeIdAndStatusInAndDateRange(
            @Param("leaveTypeId") UUID leaveTypeId,
            @Param("statuses") List<LeaveStatus> statuses,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

}
