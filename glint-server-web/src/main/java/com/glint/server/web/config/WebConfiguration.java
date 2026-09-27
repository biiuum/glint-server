package com.glint.server.web.config;

import com.glint.server.web.auth.AnonymousSessionInterceptor;
import com.glint.server.web.auth.BoundAccountInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfiguration implements WebMvcConfigurer {

    private static final String[] AUTHENTICATED_PATTERNS = {
            "/api/v1/commutes/**",
            "/api/v1/commute-preferences/**",
            "/api/v1/home/**",
            "/api/v1/achievements/**",
            "/api/v1/profiles/**",
            "/api/v1/matches/**",
            "/api/v1/topics/**",
            "/api/v1/chat/**",
            "/api/v1/board/**",
            "/api/v1/commute-reports/**",
            "/api/v1/safety/**",
            "/api/v1/metro/**"
    };

    private final AnonymousSessionInterceptor anonymousSessionInterceptor;
    private final BoundAccountInterceptor boundAccountInterceptor;

    public WebConfiguration(AnonymousSessionInterceptor anonymousSessionInterceptor,
                            BoundAccountInterceptor boundAccountInterceptor) {
        this.anonymousSessionInterceptor = anonymousSessionInterceptor;
        this.boundAccountInterceptor = boundAccountInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(anonymousSessionInterceptor)
                .addPathPatterns(AUTHENTICATED_PATTERNS)
                .excludePathPatterns("/api/v1/metro/sync");
        registry.addInterceptor(anonymousSessionInterceptor)
                .addPathPatterns("/api/v1/auth/account", "/api/v1/auth/account/**", "/api/v1/auth/sms/**");
        registry.addInterceptor(boundAccountInterceptor)
                .addPathPatterns(AUTHENTICATED_PATTERNS);
    }
}
