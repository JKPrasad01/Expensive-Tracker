package com.expensetracker.repository;

import com.expensetracker.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByPhone(String phone);

    boolean existsByPhone(String phone);
    boolean existsByEmail(String email);

    @Query("select u from User u join fetch u.roles where u.email = :email")
    Optional<User> findByEmailWithRoles(@Param("email") String email);
}
