package com.expensive.Expensive.Tracker.entity;


import jakarta.persistence.*;
import lombok.Data;

@Table(
        name = "resource_action",
        indexes = {
                @Index(name = "idx_resource", columnList = "resource_id"),
                @Index(name = "idx_action", columnList = "action_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_resource_action",
                        columnNames = {"resource_id", "action_id"}
                )
        }
)
@Entity
@Data
public class ResourceAction {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "resource_id",
            nullable = false
    )
    private Resource resource;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "action_id",
            nullable = false
    )
    private Action action;

}
