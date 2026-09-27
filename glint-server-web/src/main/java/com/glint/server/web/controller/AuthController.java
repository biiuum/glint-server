package com.glint.server.web.controller;

import com.glint.server.biz.auth.AnonymousSession;
import com.glint.server.biz.auth.AnonymousSessionService;
import com.glint.server.biz.auth.AccountService;
import com.glint.server.biz.auth.PhoneAuthService;
import com.glint.server.biz.sms.SmsService;
import com.glint.server.common.auth.AccountContracts.AccountSummary;
import com.glint.server.common.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    private final AnonymousSessionService anonymousSessionService;
    private final AccountService accountService;
    private final SmsService smsService;
    private final PhoneAuthService phoneAuthService;

    public AuthController(AnonymousSessionService anonymousSessionService, AccountService accountService,
                          SmsService smsService, PhoneAuthService phoneAuthService) {
        this.anonymousSessionService = anonymousSessionService;
        this.accountService = accountService;
        this.smsService = smsService;
        this.phoneAuthService = phoneAuthService;
    }

    @PostMapping("/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AnonymousSession> createSession() {
        AnonymousSession session = anonymousSessionService.create();
        log.info("anonymous_session_created userId={}", shortId(session.userId()));
        return ApiResponse.ok(session);
    }

    /** 读取当前用户的私密账号概要；该接口要求 Bearer 会话。 */
    @GetMapping("/account/me")
    public ApiResponse<AccountSummary> currentAccount(@RequestAttribute("authenticatedUserId") String userId) {
        return ApiResponse.ok(accountService.current(userId));
    }

    /** 将当前匿名用户绑定为正式账号；密码绝不进入日志或响应。 */
    @PutMapping("/account")
    public ApiResponse<AccountSummary> bindAccount(@RequestAttribute("authenticatedUserId") String userId,
                                                    @Valid @RequestBody AccountBindRequest request) {
        log.info("account_bind_requested userId={}, hasEmail={}, hasPhone={}", shortId(userId),
                request.email() != null && !request.email().isBlank(), request.phone() != null && !request.phone().isBlank());
        return ApiResponse.ok(accountService.bind(userId, request.email(), request.phone(), request.password()));
    }

    /** 使用邮箱或手机号与密码登录，并返回新的不透明会话。 */
    @PostMapping("/login")
    public ApiResponse<AnonymousSession> login(@Valid @RequestBody AccountLoginRequest request) {
        log.info("account_login_requested identifierType={}", request.identifier().contains("@") ? "email" : "phone");
        return ApiResponse.ok(accountService.login(request.identifier(), request.password()));
    }

    @PostMapping("/sms/send")
    public ApiResponse<SmsSendResponse> sendSms(@RequestAttribute("authenticatedUserId") String userId,
                                                @Valid @RequestBody SmsSendRequest request,
                                                HttpServletRequest httpRequest) {
        int retryAfterSeconds = smsService.sendCode(request.phone(), clientIp(httpRequest));
        log.info("sms_send_requested userId={}", shortId(userId));
        return ApiResponse.ok(new SmsSendResponse(retryAfterSeconds));
    }

    @PostMapping("/sms/login")
    public ApiResponse<AnonymousSession> smsLogin(@RequestAttribute("authenticatedUserId") String userId,
                                                  @Valid @RequestBody SmsLoginRequest request) {
        AnonymousSession session = phoneAuthService.login(userId, request.phone(), request.code());
        log.info("sms_login_succeeded userId={}", shortId(session.userId()));
        return ApiResponse.ok(session);
    }

    /** 正式账号绑定请求。邮箱和手机号至少有一项；密码只用于 BCrypt 哈希。 */
    public record AccountBindRequest(
            @Size(max = 254) String email,
            @Size(max = 32) String phone,
            @NotBlank @Size(min = 8, max = 72) String password
    ) {
    }

    /** 跨设备登录请求。 */
    public record AccountLoginRequest(
            @NotBlank @Size(max = 254) String identifier,
            @NotBlank @Size(min = 8, max = 72) String password
    ) {
    }

    public record SmsSendRequest(@NotBlank @Size(max = 32) String phone) {
    }

    public record SmsSendResponse(int retryAfterSeconds) {
    }

    public record SmsLoginRequest(
            @NotBlank @Size(max = 32) String phone,
            @NotBlank @Pattern(regexp = "^\\d{6}$") String code
    ) {
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String shortId(String userId) {
        return userId.length() <= 8 ? userId : userId.substring(0, 8);
    }
}
