package com.glint.server.common.dto;

public record ApiResponse<T>(String code, T data) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>("OK", data);
    }
}
