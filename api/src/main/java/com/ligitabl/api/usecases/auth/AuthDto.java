package com.ligitabl.api.usecases.auth;

import java.util.Map;
import java.util.Set;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AuthDto {
    public record LoginRequest(@NotBlank @Email String email, @NotBlank @Size(min = 8) String password) {}

    public record LoginResponse(String token) {}

    public record RegisterRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 2, max = 100) String displayName,
            @NotBlank @Size(min = 8, max = 100) String password) {}

    public record RegisterResponse(String publicId, String email, String displayName, Set<String> roles) {}

    public record UserInfoResponse(
            String publicId, String email, String displayName, Set<String> roles, boolean emailVerified) {}

    public record MessageResponse(String message, Map<String, Object> data) {
        public MessageResponse(String message) {
            this(message, Map.of());
        }
    }
}
