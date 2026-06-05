package com.leavesync.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "leave_types")
@Getter
@Setter
public class LeaveType extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String name;

    @Column(name = "requires_balance_tracking", nullable = false)
    private boolean requiresBalanceTracking;

    @Column(name = "requires_hr_approval", nullable = false)
    private boolean requiresHrApproval;

    @Column(name = "requires_reason", nullable = false)
    private boolean requiresReason;

}
