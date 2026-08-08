package com.expensetracker.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(
        name = "template_resource_action",
        indexes = {
                @Index(name = "idx_tra_template", columnList = "template_id"),
                @Index(name = "idx_tra_resource_action", columnList = "resource_action_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_template_resource_action",
                        columnNames = {"template_id", "resource_action_id"}
                )
        }
)
public class TemplateResourceAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false)
    private Template template;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resource_action_id", nullable = false)
    private ResourceAction resourceAction;

    @Column(name = "allowed", nullable = false)
    private boolean allowed = true;
}