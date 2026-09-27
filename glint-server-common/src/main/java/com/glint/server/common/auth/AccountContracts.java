package com.glint.server.common.auth;

/** 私密账号接口的输入输出契约。 */
public final class AccountContracts {

    private AccountContracts() {
    }

    /** 仅返回给当前已认证账号本人的账号概要；不用于社交资料或匹配接口。 */
    public record AccountSummary(String userId, String email, String phone, String accountStatus) {
    }
}
