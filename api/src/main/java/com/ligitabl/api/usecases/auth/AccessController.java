package com.ligitabl.api.usecases.auth;

import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.errors.UseCaseErrors;
import com.ligitabl.api.shared.exceptions.UseCaseException;
import com.ligitabl.api.usecases.auth.getcurrentuser.GetCurrentUserQuery;
import com.ligitabl.api.usecases.auth.getcurrentuser.GetCurrentUserUseCase;
import com.ligitabl.api.usecases.auth.getcurrentuser.UserInfo;
import com.ligitabl.model.auth.PublicId;
import com.ligitabl.model.auth.Role;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * API controller for protected endpoints.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class AccessController {

    private final GetCurrentUserUseCase getCurrentUserUseCase;

    /**
     * Get current user endpoint.
     * Accessible by any authenticated user.
     */
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(Authentication authentication) {
        String publicIdStr = authentication.getName();
        log.debug("Get current user request for: {}", publicIdStr);

        return Either.catching(() -> PublicId.create(publicIdStr), UseCaseErrors::fromException)
                .map(publicId -> new GetCurrentUserQuery(publicId))
                .flatMap(getCurrentUserUseCase::execute)
                .map(userInfo -> ResponseEntity.ok(toUserInfoResponse(userInfo)))
                .getOrElseThrow(error -> {
                    log.error("Failed to get current user: {}", error.getMessage());
                    throw new UseCaseException(error);
                });
    }

    /**
     * Admin-only endpoint.
     */
    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AuthDto.MessageResponse> adminEndpoint(Authentication authentication) {
        log.info("Admin endpoint accessed by: {}", authentication.getName());

        return ResponseEntity.ok(new AuthDto.MessageResponse(
                "Welcome to the admin area!",
                Map.of(
                        "user", authentication.getName(),
                        "authorities", authentication.getAuthorities())));
    }

    /**
     * Player-only endpoint.
     */
    @GetMapping("/player")
    @PreAuthorize("hasRole('PLAYER')")
    public ResponseEntity<AuthDto.MessageResponse> playerEndpoint(Authentication authentication) {
        log.info("Player endpoint accessed by: {}", authentication.getName());

        return ResponseEntity.ok(new AuthDto.MessageResponse(
                "Welcome to the player area!",
                Map.of(
                        "user", authentication.getName(),
                        "authorities", authentication.getAuthorities())));
    }

    public AuthDto.UserInfoResponse toUserInfoResponse(UserInfo userInfo) {
        return new AuthDto.UserInfoResponse(
                userInfo.publicId().value(),
                userInfo.email().value(),
                userInfo.displayName(),
                userInfo.roles().stream().map(Role::getValue).collect(Collectors.toSet()),
                userInfo.emailVerified());
    }
}
