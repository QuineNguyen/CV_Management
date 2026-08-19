package com.training.cvmanagementbe.entity.models;

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

    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "department_id", nullable = false)
    private UUID departmentId;

    @Column(name = "tech_lead_id", nullable = false)
    private UUID techLeadId;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;
}
