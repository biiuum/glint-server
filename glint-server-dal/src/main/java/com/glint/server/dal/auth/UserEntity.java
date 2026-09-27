package com.glint.server.dal.auth;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import java.time.Instant;

/**
 * 用户私密账号实体。
 *
 * <p>此表只保存认证所需数据，绝不可作为匹配、聊天或留言板的响应模型。</p>
 */
@Entity
@Table(name = "t_users")
public class UserEntity {

    /** 用户在业务表之间关联的内部 UUID。 */
    @Id
    private String id;
    /** 账号登录邮箱；未绑定账号的匿名用户为空。 */
    @Column(name = "email")
    private String email;
    /** 账号登录手机号；未绑定账号的匿名用户为空。 */
    @Column(name = "phone")
    private String phone;
    /** BCrypt 密码哈希，严禁记录、序列化或返回。 */
    @Column(name = "password_hash")
    private String passwordHash;
    /** 账号状态，值为 ANONYMOUS、ACTIVE 或 DISABLED。 */
    @Column(name = "account_status")
    private String accountStatus;
    /** 最近一次凭证登录成功时间。 */
    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    protected UserEntity() {
    }

    private UserEntity(String id) {
        this.id = id;
        this.accountStatus = "ANONYMOUS";
    }

    public static UserEntity create(String id) {
        return new UserEntity(id);
    }

    /** 绑定可登录凭证并将匿名用户升级为正常账号。 */
    public void bindAccount(String email, String phone, String passwordHash) {
        this.email = email;
        this.phone = phone;
        this.passwordHash = passwordHash;
        this.accountStatus = "ACTIVE";
    }

    /** 通过短信验证码绑定手机号并升级为正式账号；不要求密码。 */
    public void bindPhone(String phone) {
        this.phone = phone;
        this.accountStatus = "ACTIVE";
    }

    /** 标记一次成功的账号登录。 */
    public void markLogin(Instant loginAt) {
        this.lastLoginAt = loginAt;
    }

    /** 返回用户内部 ID。 */
    public String getId() {
        return id;
    }

    /** 返回私密登录邮箱，仅认证/本人资料接口可使用。 */
    public String getEmail() {
        return email;
    }

    /** 返回私密登录手机号，仅认证/本人资料接口可使用。 */
    public String getPhone() {
        return phone;
    }

    /** 返回 BCrypt 哈希，仅认证服务可使用。 */
    public String getPasswordHash() {
        return passwordHash;
    }

    /** 返回当前账号状态。 */
    public String getAccountStatus() {
        return accountStatus;
    }

    /** 判断该用户是否已经绑定正式登录凭证。 */
    public boolean hasPasswordCredential() {
        return "ACTIVE".equals(accountStatus) && passwordHash != null;
    }

    /** 核心功能要求已绑定手机号的正式账号。 */
    public boolean hasBoundPhone() {
        return "ACTIVE".equals(accountStatus) && phone != null && !phone.isBlank();
    }
}
