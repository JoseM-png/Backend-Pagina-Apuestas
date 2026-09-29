package com.rifas.api.dtos;

public record AdminLoginResponse(
        boolean success,
        String token,
        String username,
        String role,
        String message
) {
}
