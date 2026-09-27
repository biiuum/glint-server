package com.glint.server.common.safety;

import java.time.Instant;

public final class SafetyContracts {

    private SafetyContracts() {
    }

    public record MessageReport(String reportId, String messageId, String reporterId, String reason, String status,
                                Instant createdAt) {
    }
}
