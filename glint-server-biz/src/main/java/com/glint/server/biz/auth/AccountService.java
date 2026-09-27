package com.glint.server.biz.auth;

import com.glint.server.common.auth.AccountContracts.AccountSummary;
import com.glint.server.dal.auth.UserEntity;
import com.glint.server.dal.auth.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;

/**
 * 正式账号凭证的绑定与登录服务。
 *
 * <p>密码只在本类中短暂用于 BCrypt 校验，日志和返回值均不会包含密码、哈希或完整 token。</p>
 */
@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);
    private final UserRepository userRepository;
    private final AnonymousSessionService anonymousSessionService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(12);

    public AccountService(UserRepository userRepository, AnonymousSessionService anonymousSessionService) {
        this.userRepository = userRepository;
        this.anonymousSessionService = anonymousSessionService;
    }

    /** 读取当前用户仅对本人可见的账号资料。 */
    @Transactional(readOnly = true)
    public AccountSummary current(String userId) {
        UserEntity user = requiredUser(userId);
        return summary(user);
    }

    /** 将当前匿名用户绑定为可跨设备登录的正式账号。 */
    @Transactional
    public AccountSummary bind(String userId, String email, String phone, String password) {
        String normalizedEmail = normalizeEmail(email);
        String normalizedPhone = normalizePhone(phone);
        validateIdentifier(normalizedEmail, normalizedPhone);
        UserEntity user = requiredUser(userId);
        if (user.hasPasswordCredential()) {
            throw new IllegalArgumentException("当前用户已绑定账号，请使用账号资料修改流程");
        }
        ensureAvailable(userId, normalizedEmail, normalizedPhone);
        user.bindAccount(normalizedEmail, normalizedPhone, passwordEncoder.encode(password));
        log.info("account_bound userId={}, hasEmail={}, hasPhone={}", shortId(userId), normalizedEmail != null, normalizedPhone != null);
        return summary(userRepository.save(user));
    }

    /** 校验邮箱或手机号加密码，并为既有用户签发新的不透明会话。 */
    @Transactional
    public AnonymousSession login(String identifier, String password) {
        String normalizedIdentifier = normalizeIdentifier(identifier);
        UserEntity user = (normalizedIdentifier.contains("@")
                ? userRepository.findByEmail(normalizedIdentifier)
                : userRepository.findByPhone(normalizedIdentifier))
                .orElseThrow(() -> new IllegalArgumentException("账号或密码不正确"));
        if (!user.hasPasswordCredential() || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new IllegalArgumentException("账号或密码不正确");
        }
        user.markLogin(Instant.now());
        userRepository.save(user);
        log.info("account_login_succeeded userId={}, identifierType={}", shortId(user.getId()),
                normalizedIdentifier.contains("@") ? "email" : "phone");
        return anonymousSessionService.issueForExistingUser(user.getId());
    }

    private UserEntity requiredUser(String userId) {
        return userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("用户不存在"));
    }

    private void ensureAvailable(String userId, String email, String phone) {
        if (email != null && userRepository.findByEmail(email).filter(other -> !other.getId().equals(userId)).isPresent()) {
            throw new IllegalArgumentException("该邮箱已被绑定");
        }
        if (phone != null && userRepository.findByPhone(phone).filter(other -> !other.getId().equals(userId)).isPresent()) {
            throw new IllegalArgumentException("该手机号已被绑定");
        }
    }

    private AccountSummary summary(UserEntity user) {
        return new AccountSummary(user.getId(), user.getEmail(), user.getPhone(), user.getAccountStatus());
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) return null;
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) throw new IllegalArgumentException("邮箱格式无效");
        return normalized;
    }

    private String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) return null;
        String normalized = phone.replaceAll("[\\s-]", "");
        if (!normalized.matches("^\\+?[1-9]\\d{6,30}$")) throw new IllegalArgumentException("手机号格式无效");
        return normalized;
    }

    private String normalizeIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) throw new IllegalArgumentException("账号不能为空");
        return identifier.contains("@") ? normalizeEmail(identifier) : normalizePhone(identifier);
    }

    private void validateIdentifier(String email, String phone) {
        if (email == null && phone == null) throw new IllegalArgumentException("邮箱和手机号至少填写一项");
    }

    private String shortId(String userId) {
        return userId.length() <= 8 ? userId : userId.substring(0, 8);
    }
}
