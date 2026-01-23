package com.streamchat.repository;

import com.streamchat.model.entity.UserStreamRole;
import com.streamchat.model.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for UserStreamRole entity operations.
 */
@Repository
public interface UserStreamRoleRepository extends JpaRepository<UserStreamRole, Long> {

    /**
     * Find roles for user in a stream.
     *
     * @param userId the user ID
     * @param streamId the stream ID
     * @return list of roles
     */
    @Query("SELECT r FROM UserStreamRole r WHERE r.user.id = :userId " +
            "AND r.stream.id = :streamId")
    List<UserStreamRole> findByUserIdAndStreamId(Long userId, Long streamId);

    /**
     * Find specific role for user in stream.
     *
     * @param userId the user ID
     * @param streamId the stream ID
     * @param role the role
     * @return optional role
     */
    @Query("SELECT r FROM UserStreamRole r WHERE r.user.id = :userId " +
            "AND r.stream.id = :streamId AND r.role = :role")
    Optional<UserStreamRole> findByUserIdAndStreamIdAndRole(Long userId, Long streamId, Role role);

    /**
     * Check if user has moderator or broadcaster role.
     *
     * @param streamId the stream ID
     * @param userId the user ID
     * @return true if can moderate
     */
    @Query("SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END FROM UserStreamRole r " +
            "WHERE r.stream.id = :streamId AND r.user.id = :userId " +
            "AND (r.role = 'ROLE_MODERATOR' OR r.role = 'ROLE_BROADCASTER')")
    boolean hasModeratorRole(Long streamId, Long userId);

    /**
     * Find all moderators for a stream.
     *
     * @param streamId the stream ID
     * @return list of moderator roles
     */
    @Query("SELECT r FROM UserStreamRole r WHERE r.stream.id = :streamId " +
            "AND r.role = 'ROLE_MODERATOR'")
    List<UserStreamRole> findModerators(Long streamId);

    /**
     * Delete role.
     *
     * @param userId the user ID
     * @param streamId the stream ID
     * @param role the role
     */
    void deleteByUserIdAndStreamIdAndRole(Long userId, Long streamId, Role role);
}