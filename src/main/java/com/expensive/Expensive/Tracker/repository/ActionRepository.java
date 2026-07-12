package com.expensive.Expensive.Tracker.repository;

import com.expensive.Expensive.Tracker.entity.Action;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface ActionRepository extends JpaRepository<Action, Integer> {

    boolean existsByActionNameIgnoreCase(String actionName);

    boolean existsByActionKeyIgnoreCase(String actionKey);
}