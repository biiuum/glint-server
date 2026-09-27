package com.glint.server.biz.auth;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;

public record AnonymousSession(
        String userId,
        String sessionToken,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ssX", timezone = "UTC")
        Instant expiresAt
) {
}
