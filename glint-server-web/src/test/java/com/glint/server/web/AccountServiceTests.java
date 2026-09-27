package com.glint.server.web;

import com.glint.server.biz.auth.AccountService;
import com.glint.server.biz.auth.AnonymousSession;
import com.glint.server.biz.auth.AnonymousSessionService;
import com.glint.server.dal.auth.UserEntity;
import com.glint.server.dal.auth.UserRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 账号凭证服务的无数据库回归测试。 */
class AccountServiceTests {

    @Test
    void bindingStoresOnlyBcryptHashAndReturnsNoPassword() {
        UserRepository users = mock(UserRepository.class);
        AnonymousSessionService sessions = mock(AnonymousSessionService.class);
        UserEntity user = UserEntity.create("user-12345678");
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        when(users.findByEmail("me@example.com")).thenReturn(Optional.empty());
        when(users.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var account = new AccountService(users, sessions).bind(user.getId(), "ME@example.com", null, "password-123");

        assertThat(user.getPasswordHash()).isNotEqualTo("password-123");
        assertThat(user.getPasswordHash()).startsWith("$2");
        assertThat(account.email()).isEqualTo("me@example.com");
        assertThat(account.accountStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void successfulLoginIssuesFreshOpaqueSessionForTheExistingUser() {
        UserRepository users = mock(UserRepository.class);
        AnonymousSessionService sessions = mock(AnonymousSessionService.class);
        UserEntity user = UserEntity.create("user-12345678");
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        when(users.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        new AccountService(users, sessions).bind(user.getId(), null, "13800138000", "password-123");
        when(users.findByPhone("13800138000")).thenReturn(Optional.of(user));
        AnonymousSession expected = new AnonymousSession(user.getId(), "opaque-token", Instant.parse("2026-09-01T00:00:00Z"));
        when(sessions.issueForExistingUser(user.getId())).thenReturn(expected);

        AnonymousSession actual = new AccountService(users, sessions).login("13800138000", "password-123");

        assertThat(actual).isEqualTo(expected);
        assertThat(actual.sessionToken()).isNotEqualTo("password-123");
    }
}
