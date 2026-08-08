package com.expensive.Expensive.Tracker.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

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
@Getter
@Setter
public class ResourceAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resource_id", nullable = false)
    private Resource resource;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "action_id", nullable = false)
    private Action action;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ResourceAction that)) return false;
        // Only equal if both have a persisted id and they match.
        // Transient (unsaved) entities are never equal to anything but themselves.
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        // Constant hashCode, deliberately NOT derived from id.
        // id is null until persisted; a hashCode that changes after
        // insertion into a HashSet/HashMap corrupts that collection.
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "ResourceAction{" +
                "id=" + id +
                '}';
    }
}