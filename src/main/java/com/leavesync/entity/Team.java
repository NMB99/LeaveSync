package com.leavesync.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "teams")
@Getter
@Setter
public class Team extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(name = "manager_id", nullable = false)
    private UUID managerId;
}
