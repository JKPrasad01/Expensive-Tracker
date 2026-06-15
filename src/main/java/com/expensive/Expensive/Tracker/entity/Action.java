package com.expensive.Expensive.Tracker.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Entity
@Table(
    name = "actions",
    indexes = {
        @Index(name = "idx_action_key", columnList = "action_key"),
        @Index(name = "idx_action_name", columnList = "action_name")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Action {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "action_key", nullable = false, unique = true, length = 50, updatable = false)
    private String actionKey;

    @Setter
    @Column(name = "action_name", nullable = false, unique = true, length = 30)
    private String actionName;

    @Setter
    @Column(length = 255)
    private String description;

    public Action(String actionKey, String actionName, String description) {
        this.actionKey = validateKey(actionKey);
        this.actionName = actionName;
        this.description = description;
    }

    public void initializeKey(String actionKey) {
        this.actionKey = validateKey(actionKey);
    }

    private String validateKey(String actionKey) {
        if (actionKey == null || actionKey.isBlank()) {
            throw new IllegalArgumentException("Please provide a valid action key");
        }

        return actionKey.trim().toUpperCase();
    }
}