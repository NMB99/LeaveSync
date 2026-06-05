package com.leavesync.entity;

import com.leavesync.enums.LeaveStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
@Getter
@Setter
public class AuditLog {

    @Id
    @UuidGenerator
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "leave_request_id", nullable = false, updatable = false)
    private UUID leaveRequestId;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", updatable = false)
    private LeaveStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, updatable = false)
    private LeaveStatus newStatus;

    @Column(name = "actioned_by", updatable = false)
    private UUID actionedBy;

    @Column(updatable = false)
    private String notes;

    @CreationTimestamp
    @Column(name = "changed_at", nullable = false, updatable = false)
    private LocalDateTime changedAt;

}
