package com.glint.server.biz.auth;

/** 当前匿名会话无权访问指定的用户资源。 */
public class ResourceAccessDeniedException extends RuntimeException {
    public ResourceAccessDeniedException(String message) {
        super(message);
    }
}
