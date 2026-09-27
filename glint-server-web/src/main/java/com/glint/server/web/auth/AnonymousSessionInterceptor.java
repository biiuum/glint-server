package com.glint.server.web.auth;

import com.glint.server.biz.auth.AnonymousSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AnonymousSessionInterceptor implements HandlerInterceptor {

    public static final String AUTHENTICATED_USER_ID = "authenticatedUserId";
    private static final String BEARER_PREFIX = "Bearer ";
    private final AnonymousSessionService anonymousSessionService;

    public AnonymousSessionInterceptor(AnonymousSessionService anonymousSessionService) {
        this.anonymousSessionService = anonymousSessionService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)
                || authorization.length() == BEARER_PREFIX.length()) {
            throw new com.glint.server.biz.auth.InvalidAnonymousSessionException();
        }
        request.setAttribute(AUTHENTICATED_USER_ID,
                anonymousSessionService.authenticatedUserId(authorization.substring(BEARER_PREFIX.length())));
        return true;
    }
}
