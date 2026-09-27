package com.glint.server.web.auth;

import com.glint.server.biz.auth.AccountRequiredException;
import com.glint.server.biz.auth.PhoneAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class BoundAccountInterceptor implements HandlerInterceptor {

    private final PhoneAuthService phoneAuthService;

    public BoundAccountInterceptor(PhoneAuthService phoneAuthService) {
        this.phoneAuthService = phoneAuthService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!requiresBoundAccount(request)) {
            return true;
        }
        Object userId = request.getAttribute(AnonymousSessionInterceptor.AUTHENTICATED_USER_ID);
        if (!(userId instanceof String id) || !phoneAuthService.hasBoundPhone(id)) {
            throw new AccountRequiredException();
        }
        return true;
    }

    static boolean requiresBoundAccount(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        if ("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method) || "OPTIONS".equalsIgnoreCase(method)) {
            return false;
        }
        if (path.startsWith("/api/v1/auth/")) {
            return false;
        }
        if (path.startsWith("/api/v1/metro/sync")) {
            return false;
        }
        return true;
    }
}
