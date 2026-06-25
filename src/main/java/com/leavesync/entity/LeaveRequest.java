package com.leavesync.entity;

import com.leavesync.enums.LeaveStatus;
import com.leavesync.enums.Role;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "leave_requests")
@Getter
@Setter
public class LeaveRequest extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "leave_type_id", nullable = false)
    private UUID leaveTypeId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "is_half_day", nullable = false)
    private boolean isHalfDay = false;

    @Column
    private String reason;

    @Column(name = "total_working_days", nullable = false, precision = 4, scale = 1)
    private BigDecimal totalWorkingDays;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private LeaveStatus status;

    @Column(name = "assigned_to", nullable = false)
    @Enumerated(EnumType.STRING)
    private Role assignedTo;

    @Column(name = "actioned_by")
    private UUID actionedBy;

    @Column(name = "notice_period_warning", nullable = false)
    private boolean noticePeriodWarning = false;

    @Column(name = "overlap_warning", nullable = false)
    private boolean overlapWarning = false;

    @Column(name = "escalation_start_date")
    private LocalDate escalationStartDate;

}
