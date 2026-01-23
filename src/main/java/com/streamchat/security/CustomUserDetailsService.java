package com.streamchat.security;

import com.streamchat.model.entity.User;
import com.streamchat.model.entity.UserRole;
import com.streamchat.repository.UserRepository;
import com.streamchat.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Custom UserDetailsService implementation for Spring Security.
 * Loads user-specific data including roles during authentication.
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;

    /**
     * Load user by username for authentication.
     * Includes all global roles assigned to the user.
     *
     * @param username the username
     * @return UserDetails object with authorities
     * @throws UsernameNotFoundException if user not found
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User not found with username: " + username));

        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPasswordHash(),
                user.getIsActive(),
                true, // accountNonExpired
                true, // credentialsNonExpired
                true, // accountNonLocked
                getAuthorities(user.getId())
        );
    }

    /**
     * Get user authorities/roles from database.
     * If user has no roles, returns default ROLE_USER.
     *
     * @param userId the user ID
     * @return collection of granted authorities
     */
    private Collection<? extends GrantedAuthority> getAuthorities(Long userId) {
        List<UserRole> userRoles = userRoleRepository.findByUserId(userId);

        if (userRoles.isEmpty()) {
            // Default role for users without explicit roles
            return List.of(new SimpleGrantedAuthority("ROLE_USER"));
        }

        return userRoles.stream()
                .map(userRole -> new SimpleGrantedAuthority(userRole.getRole().name()))
                .collect(Collectors.toList());
    }
}