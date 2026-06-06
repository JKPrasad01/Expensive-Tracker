package com.expensive.Expensive.Tracker.repository;

import com.expensive.Expensive.Tracker.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByPhone(String phone);

    boolean existsByPhone(String phone);
    boolean existsByEmail(String email);
}
