package com.glint.server.biz.sms;

public interface SmsSender {

    SmsSendResult sendCode(String phone, String code);
}
