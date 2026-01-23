package com.streamchat.repository;

import com.streamchat.model.entity.UserRole;
import com.streamchat.model.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for UserRole entity operations.
 */
@Repository
public interface UserRoleRepository extends JpaRepository<UserRole, Long> {

    /**
     * Find all roles for a user.
     *
     * @param userId the user ID
     * @return list of user roles
     */
    @Query("SELECT r FROM UserRole r WHERE r.user.id = :userId")
    List<UserRole> findByUserId(Long userId);

    /**
     * Check if user has a specific role.
     *
     * @param userId the user ID
     * @param role the role
     * @return true if user has role
     */
    @Query("SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END FROM UserRole r " +
            "WHERE r.user.id = :userId AND r.role = :role")
    boolean existsByUserIdAndRole(Long userId, Role role);

    /**
     * Delete a specific role from user.
     *
     * @param userId the user ID
     * @param role the role
     */
    void deleteByUserIdAndRole(Long userId, Role role);
}