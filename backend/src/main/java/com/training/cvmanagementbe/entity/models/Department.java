package com.training.cvmanagementbe.entity.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "departments")
@Getter
@Setter
public class Department extends BaseEntity {

    // Short label shown in narrow places, e.g. "P.KTCN". Globally unique.
    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    // Full readable name, e.g. "Phòng Kỹ thuật công nghệ". Not necessarily unique.
    @Column(name = "name", nullable = false)
    private String name;

    // Null means the node sits at root level
    @Column(name = "parent_department_id")
    private UUID parentDepartmentId;

    // Order among siblings only. Numbered with a step of 10 to allow inserts
    @Column(name = "display_order", nullable = false)
    private int displayOrder;
}
