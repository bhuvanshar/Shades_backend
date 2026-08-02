package com.sunglassstore.repository;

import com.sunglassstore.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    @EntityGraph(attributePaths = {"roles"})
    @Query("SELECT u FROM User u WHERE u.userId = :userId")
    Optional<User> findByIdWithRoles(@Param("userId") Long userId);

    boolean existsByEmailIgnoreCase(String email);

    @EntityGraph(attributePaths = {"roles"})
    @Query(value = "SELECT DISTINCT u FROM User u JOIN u.roles r WHERE r.roleName = 'CUSTOMER' " +
            "AND NOT EXISTS (SELECT 1 FROM User u2 JOIN u2.roles ar WHERE u2 = u AND ar.roleName = 'ADMIN')",
            countQuery = "SELECT COUNT(DISTINCT u) FROM User u JOIN u.roles r WHERE r.roleName = 'CUSTOMER' " +
                    "AND NOT EXISTS (SELECT 1 FROM User u2 JOIN u2.roles ar WHERE u2 = u AND ar.roleName = 'ADMIN')")
    Page<User> findCustomers(Pageable pageable);
}
