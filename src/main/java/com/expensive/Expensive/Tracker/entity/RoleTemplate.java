package com.expensive.Expensive.Tracker.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Setter
@Getter
@Table(
        name = "role_template",
        indexes = {
                @Index(name = "idx_rt_template", columnList = "template_id"),
                @Index(name = "idx_rt_role", columnList = "role_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_role_template",
                        columnNames = {"template_id", "role_id"}
                )
        }
)
public class RoleTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false)
    private Template template;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @Column(name = "allowed", nullable = false)
    private boolean allowed;
}