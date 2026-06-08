package com.leavesync.repository;

import com.leavesync.entity.LeaveRequest;
import com.leavesync.enums.LeaveStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, UUID> {
    
    List<LeaveRequest> findByUserId(UUID userId);

    List<LeaveRequest> findByStatus(LeaveStatus status);

    List<LeaveRequest> findByUserIdAndStatus(UUID userId, LeaveStatus status);

    List<LeaveRequest> findByUserIdAndStatusAndEndDateGreaterThanEqual(UUID userId, LeaveStatus status, LocalDate endDate);

}
