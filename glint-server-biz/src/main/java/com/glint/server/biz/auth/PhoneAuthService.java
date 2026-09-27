package com.glint.server.biz.auth;

import com.glint.server.biz.sms.SmsService;
import com.glint.server.dal.auth.UserEntity;
import com.glint.server.dal.auth.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
public class PhoneAuthService {

    private static final Logger log = LoggerFactory.getLogger(PhoneAuthService.class);
    private final UserRepository userRepository;
    private final AnonymousSessionService anonymousSessionService;
    private final SmsService smsService;

    public PhoneAuthService(UserRepository userRepository, AnonymousSessionService anonymousSessionService,
                            SmsService smsService) {
        this.userRepository = userRepository;
        this.anonymousSessionService = anonymousSessionService;
        this.smsService = smsService;
    }

    @Transactional
    public AnonymousSession login(String currentUserId, String phone, String code) {
        String normalized = SmsService.normalizePhone(phone);
        if (!smsService.verifyCode(normalized, code)) {
            throw new IllegalArgumentException("验证码错误或已过期");
        }
        Optional<UserEntity> existing = userRepository.findByPhone(normalized);
        if (existing.isPresent()) {
            UserEntity user = existing.get();
            user.markLogin(Instant.now());
            userRepository.save(user);
            log.info("sms_login_existing userId={}", shortId(user.getId()));
            return anonymousSessionService.issueForExistingUser(user.getId());
        }
        UserEntity current = userRepository.findById(currentUserId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        current.bindPhone(normalized);
        current.markLogin(Instant.now());
        userRepository.save(current);
        log.info("sms_login_bound_guest userId={}", shortId(current.getId()));
        return anonymousSessionService.issueForExistingUser(current.getId());
    }

    public boolean hasBoundPhone(String userId) {
        return userRepository.findById(userId).filter(UserEntity::hasBoundPhone).isPresent();
    }

    private String shortId(String userId) {
        return userId.length() <= 8 ? userId : userId.substring(0, 8);
    }
}
