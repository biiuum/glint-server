package com.glint.server.biz.sms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class SmsService {

    private static final String CODE_KEY = "sms:code:";
    private static final String PHONE_LIMIT_KEY = "sms:limit:phone:";
    private static final String IP_LIMIT_KEY = "sms:limit:ip:";
    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    private static final Duration PHONE_COOLDOWN = Duration.ofSeconds(60);
    private static final Duration IP_WINDOW = Duration.ofHours(1);
    private static final int IP_LIMIT = 20;
    private static final Logger log = LoggerFactory.getLogger(SmsService.class);

    private final StringRedisTemplate redisTemplate;
    private final SmsSender smsSender;
    private final SmsProperties properties;

    public SmsService(StringRedisTemplate redisTemplate, SmsSender smsSender, SmsProperties properties) {
        this.redisTemplate = redisTemplate;
        this.smsSender = smsSender;
        this.properties = properties;
    }

    public int sendCode(String phone, String clientIp) {
        String normalized = normalizePhone(phone);
        if (properties.whitelist().contains(normalized)) {
            log.info("sms_whitelist_skip_send phone={}", PhoneMask.mask(normalized));
            return (int) PHONE_COOLDOWN.toSeconds();
        }
        Long phoneTtl = redisTemplate.getExpire(PHONE_LIMIT_KEY + normalized);
        if (phoneTtl != null && phoneTtl > 0) {
            throw new IllegalArgumentException("请" + phoneTtl + "秒后再试");
        }
        String ipKey = IP_LIMIT_KEY + blankTo(clientIp, "unknown");
        Long ipCount = redisTemplate.opsForValue().increment(ipKey);
        if (ipCount != null && ipCount == 1) {
            redisTemplate.expire(ipKey, IP_WINDOW);
        }
        if (ipCount != null && ipCount > IP_LIMIT) {
            throw new IllegalArgumentException("请求过于频繁，请稍后再试");
        }
        String code = resolveCode();
        redisTemplate.opsForValue().set(CODE_KEY + normalized, code, CODE_TTL);
        redisTemplate.opsForValue().set(PHONE_LIMIT_KEY + normalized, "1", PHONE_COOLDOWN);
        SmsSendResult result = smsSender.sendCode(normalized, code);
        if (!result.success()) {
            redisTemplate.delete(CODE_KEY + normalized);
            redisTemplate.delete(PHONE_LIMIT_KEY + normalized);
            throw new IllegalArgumentException("短信发送失败，请稍后重试");
        }
        return (int) PHONE_COOLDOWN.toSeconds();
    }

    public boolean verifyCode(String phone, String code) {
        String normalized = normalizePhone(phone);
        if (properties.whitelist().contains(normalized)) {
            log.info("sms_whitelist_skip_verify phone={}", PhoneMask.mask(normalized));
            return code != null && code.matches("^\\d{6}$");
        }
        if (!StringUtils.hasText(code)) {
            return false;
        }
        String key = CODE_KEY + normalized;
        String stored = redisTemplate.opsForValue().get(key);
        if (stored == null || !stored.equals(code.trim())) {
            return false;
        }
        redisTemplate.delete(key);
        return true;
    }

    public static String normalizePhone(String phone) {
        if (phone == null) {
            throw new IllegalArgumentException("手机号格式无效");
        }
        String normalized = phone.replaceAll("[\\s-]", "");
        if (normalized.startsWith("+86")) {
            normalized = normalized.substring(3);
        }
        if (!normalized.matches("^1[3-9]\\d{9}$")) {
            throw new IllegalArgumentException("手机号格式无效");
        }
        return normalized;
    }

    private String resolveCode() {
        if (StringUtils.hasText(properties.getDevFixedCode()) && properties.getDevFixedCode().matches("^\\d{6}$")) {
            return properties.getDevFixedCode();
        }
        return String.valueOf(ThreadLocalRandom.current().nextInt(100000, 1000000));
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
