package com.glint.server.biz.auth;

import com.glint.server.dal.auth.AnonymousSessionEntity;
import com.glint.server.dal.auth.AnonymousSessionRepository;
import com.glint.server.dal.auth.UserEntity;
import com.glint.server.dal.auth.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Service
/** 签发和验证不透明匿名会话；token 明文永不记录。 */
public class AnonymousSessionService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Logger log = LoggerFactory.getLogger(AnonymousSessionService.class);
    private final AnonymousSessionRepository anonymousSessionRepository;
    private final UserRepository userRepository;
    private final long sessionTtlDays;

    public AnonymousSessionService(AnonymousSessionRepository anonymousSessionRepository, UserRepository userRepository,
                                   @Value("${glint.auth.session-ttl-days:30}") long sessionTtlDays) {
        this.anonymousSessionRepository = anonymousSessionRepository;
        this.userRepository = userRepository;
        this.sessionTtlDays = sessionTtlDays;
    }

    @Transactional
    public AnonymousSession create() {
        String userId = UUID.randomUUID().toString();
        userRepository.save(UserEntity.create(userId));
        return issueForExistingUser(userId);
    }

    /** 为已存在用户签发一个新的不透明 Bearer 会话。 */
    @Transactional
    public AnonymousSession issueForExistingUser(String userId) {
        String token = token();
        Instant expiresAt = Instant.now().plus(Duration.ofDays(sessionTtlDays));
        anonymousSessionRepository.save(AnonymousSessionEntity.active(
                UUID.randomUUID().toString(), userId, digest(token), expiresAt
        ));
        log.info("anonymous_session_issued userId={}, expiresAt={}", shortId(userId), expiresAt);
        return new AnonymousSession(userId, token, expiresAt);
    }

    @Transactional(readOnly = true)
    public String authenticatedUserId(String token) {
        String userId = anonymousSessionRepository.findByTokenDigest(digest(token))
                .filter(session -> session.isActiveAt(Instant.now()))
                .map(AnonymousSessionEntity::getUserId)
                .orElseThrow(InvalidAnonymousSessionException::new);
        log.info("anonymous_session_authenticated userId={}", shortId(userId));
        return userId;
    }

    private String token() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private String digest(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }

    private String shortId(String value) { return value.length() <= 8 ? value : value.substring(0, 8); }
}
