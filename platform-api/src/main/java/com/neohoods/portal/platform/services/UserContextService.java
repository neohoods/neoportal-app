package com.neohoods.portal.platform.services;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.neohoods.portal.platform.entities.UserEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserContextService {

    private final UsersService usersService;

    // Simple in-memory cache - in production, consider using Redis or Caffeine
    private final ConcurrentMap<UUID, String> usernameCache = new ConcurrentHashMap<>();

    public Mono<String> getUsername(UUID userId) {
        // Check cache first
        String cachedUsername = usernameCache.get(userId);
        if (cachedUsername != null) {
            log.debug("Username found in cache for user: {}", userId);
            return Mono.just(cachedUsername);
        }

        // If not in cache, fetch from database
        log.debug("Username not in cache, fetching from database for user: {}", userId);
        return usersService.getUserById(userId)
                .map(user -> {
                    String username = user.getEmail(); // or user.getUsername() depending on what you want
                    usernameCache.put(userId, username);
                    log.debug("Username cached for user: {} -> {}", userId, username);
                    return username;
                })
                .doOnError(error -> log.warn("Failed to fetch username for user: {}, error: {}", userId,
                        error.getMessage()));
    }

    public void clearCache(UUID userId) {
        usernameCache.remove(userId);
        log.debug("Cache cleared for user: {}", userId);
    }

    public void clearAllCache() {
        usernameCache.clear();
        log.debug("All user cache cleared");
    }

    public UserEntity getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserEntity) {
            return (UserEntity) authentication.getPrincipal();
        }
        return null;
    }
}
