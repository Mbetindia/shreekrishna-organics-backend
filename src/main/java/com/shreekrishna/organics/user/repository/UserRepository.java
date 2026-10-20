
package com.shreekrishna.organics.user.repository;

import com.shreekrishna.organics.user.entity.Role;
import com.shreekrishna.organics.user.entity.User;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    // Existing Email Login
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    // Mobile OTP Customer Login
    Optional<User> findByMobile(String mobile);

    boolean existsByMobile(String mobile);

    // Admin Dashboard
    long countByRole(Role role);

    // Admin Customer Listing
    List<User> findByRoleOrderByCreatedAtDesc(Role role);
}
