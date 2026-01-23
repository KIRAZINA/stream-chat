package com.streamchat.repository;

import com.streamchat.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for User entity operations.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Find user by username.
     *
     * @param username the username
     * @return optional user
     */
    Optional<User> findByUsername(String username);

    /**
     * Find user by email.
     *
     * @param email the email
     * @return optional user
     */
    Optional<User> findByEmail(String email);

    /**
     * Check if username exists.
     *
     * @param username the username
     * @return true if exists
     */
    boolean existsByUsername(String username);

    /**
     * Check if email exists.
     *
     * @param email the email
     * @return true if exists
     */
    boolean existsByEmail(String email);
}