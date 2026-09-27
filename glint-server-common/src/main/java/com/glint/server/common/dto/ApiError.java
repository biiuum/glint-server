package com.glint.server.common.dto;

public record ApiError(String code, String message, String traceId) {
}
