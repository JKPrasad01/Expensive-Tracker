package com.expensive.Expensive.Tracker.repository;

import com.expensive.Expensive.Tracker.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RoleRepository extends JpaRepository<Role,Integer> {

    boolean existsByRoleKey(String roleKey);
}
