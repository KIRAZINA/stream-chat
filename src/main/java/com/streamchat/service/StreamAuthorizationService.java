package com.streamchat.service;

import com.streamchat.model.entity.Stream;
import com.streamchat.model.entity.User;
import com.streamchat.model.enums.Role;
import com.streamchat.repository.StreamRepository;
import com.streamchat.repository.UserRepository;
import com.streamchat.repository.UserRoleRepository;
import com.streamchat.repository.UserStreamRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Authorization helpers for stream-scoped permissions.
 */
@Service
@RequiredArgsConstructor
public class StreamAuthorizationService {

    private final StreamRepository streamRepository;
    private final UserRepository userRepository;
    private final UserStreamRoleRepository userStreamRoleRepository;
    private final UserRoleRepository userRoleRepository;

    public boolean canModerate(String streamKey, String username) {
        if (username == null || streamKey == null) {
            return false;
        }
        Stream stream = streamRepository.findByStreamKey(streamKey).orElse(null);
        User user = userRepository.findByUsername(username).orElse(null);
        if (stream == null || user == null) {
            return false;
        }
        if (isAdmin(user.getId())) {
            return true;
        }
        if (stream.getUser().getId().equals(user.getId())) {
            return true;
        }
        return userStreamRoleRepository.hasModeratorRole(stream.getId(), user.getId());
    }

    public boolean canManageSettings(String streamKey, String username) {
        if (username == null || streamKey == null) {
            return false;
        }
        Stream stream = streamRepository.findByStreamKey(streamKey).orElse(null);
        User user = userRepository.findByUsername(username).orElse(null);
        if (stream == null || user == null) {
            return false;
        }
        if (isAdmin(user.getId())) {
            return true;
        }
        if (stream.getUser().getId().equals(user.getId())) {
            return true;
        }
        return userStreamRoleRepository
                .findByUserIdAndStreamIdAndRole(user.getId(), stream.getId(), Role.ROLE_BROADCASTER)
                .isPresent();
    }

    private boolean isAdmin(Long userId) {
        return userRoleRepository.existsByUserIdAndRole(userId, Role.ROLE_ADMIN);
    }
}
