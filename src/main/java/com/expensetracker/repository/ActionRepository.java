package com.expensetracker.repository;

import com.expensetracker.entity.Action;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface ActionRepository extends JpaRepository<Action, Long> {

    boolean existsByActionNameIgnoreCase(String actionName);

    boolean existsByActionKeyIgnoreCase(String actionKey);
}