package com.glint.server.biz.sms;

public record SmsSendResult(boolean success, String errorCode, String errorMessage) {

    public static SmsSendResult ok() {
        return new SmsSendResult(true, null, null);
    }

    public static SmsSendResult fail(String errorCode, String errorMessage) {
        return new SmsSendResult(false, errorCode, errorMessage);
    }
}
