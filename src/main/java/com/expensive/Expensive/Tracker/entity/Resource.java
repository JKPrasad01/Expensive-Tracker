package com.expensive.Expensive.Tracker.entity;

import com.expensive.Expensive.Tracker.enums.ResourceType;
import com.expensive.Expensive.Tracker.exception.ResourceKeyNotFoundException;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Table(name = "resource")
@Entity
@Getter
public class Resource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @Column(
            name = "resource_name",
            nullable = false,
            length = 100
    )
    private String resourceName;

    @Column(
            name = "resource_key",
            nullable = false,
            unique = true,
            updatable = false,
            length = 50
    )
    private String resourceKey;

    @Setter
    @Column(name = "path", length = 255)
    private String path;

    @Setter
    @Column(name = "display_order")
    private Integer displayOrder;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, length = 30)
    private ResourceType resourceType;

    @Setter
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Resource parent;

    @OneToMany(
            mappedBy = "parent",
            fetch = FetchType.LAZY,
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<Resource> children = new ArrayList<>();

    public void initiateKey(String resourceKey) {
        if (this.resourceKey != null) {
            throw new IllegalStateException("resourceKey is already initialized and cannot be changed");
        }
        this.resourceKey = validateKey(resourceKey);
    }

    private String validateKey(String resourceKey) {

        if (resourceKey == null || resourceKey.isBlank()) {
            throw new ResourceKeyNotFoundException("invalid resource key");
        }

        return resourceKey.trim().toUpperCase();
    }

    /**
     * Returns a read-only view of the children.
     * Use {@link #addChild(Resource)} / {@link #removeChild(Resource)} to mutate.
     */
    public List<Resource> getChildren() {
        return Collections.unmodifiableList(children);
    }

    public void addChild(Resource child) {

        Objects.requireNonNull(child, "child cannot be null");

        if (!children.contains(child)) {
            children.add(child);
            child.setParent(this);
        }
    }

    public void removeChild(Resource child) {

        if (child == null) {
            return;
        }

        children.remove(child);
        child.setParent(null);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Resource that)) return false;
        return resourceKey != null && resourceKey.equals(that.resourceKey);
    }

    @Override
    public int hashCode() {
        // Constant hashCode, deliberately NOT derived from resourceKey.
        // resourceKey can be null at construction and set later via
        // initiateKey(); a hashCode that changes after an entity has
        // been placed in a HashSet/HashMap corrupts that collection
        // (the entity becomes unfindable in its original bucket).
        // A constant hashCode trades hash distribution for lifecycle
        // safety, which is the right tradeoff for small collections
        // like a resource tree.
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "Resource{" +
                "id=" + id +
                ", resourceName='" + resourceName + '\'' +
                ", resourceKey='" + resourceKey + '\'' +
                ", resourceType=" + resourceType +
                ", isActive=" + isActive +
                '}';
    }
}