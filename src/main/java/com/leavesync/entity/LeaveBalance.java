package com.leavesync.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "leave_balances", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "year"}))
@Getter
@Setter
public class LeaveBalance extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private Integer year;

    @Column(name = "total_entitlement", nullable = false, precision = 4, scale = 1)
    private BigDecimal totalEntitlement;

    @Column(name = "carried_over", nullable = false, precision = 4, scale = 1)
    private BigDecimal carriedOver = BigDecimal.ZERO;

    @Column(name = "leave_used", nullable = false, precision = 4, scale = 1)
    private BigDecimal leaveUsed = BigDecimal.ZERO;

    @Column(name = "pending_days", nullable = false, precision = 4, scale = 1)
    private BigDecimal pendingDays = BigDecimal.ZERO;

}
