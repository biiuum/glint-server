package com.glint.server.biz.sms;

import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.dysmsapi20170525.models.SendSmsResponse;
import com.aliyun.teautil.models.RuntimeOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "glint.sms.aliyun", name = "enabled", havingValue = "true")
public class AliyunSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(AliyunSmsSender.class);
    private final Client client;
    private final SmsProperties properties;

    public AliyunSmsSender(Client client, SmsProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public SmsSendResult sendCode(String phone, String code) {
        try {
            SendSmsRequest request = new SendSmsRequest()
                    .setPhoneNumbers(phone)
                    .setSignName(properties.getAliyun().getSignName())
                    .setTemplateCode(properties.getAliyun().getTemplateCode())
                    .setTemplateParam("{\"code\":\"" + code + "\"}");
            SendSmsResponse response = client.sendSmsWithOptions(request, new RuntimeOptions());
            if (response != null && response.getBody() != null && "OK".equals(response.getBody().getCode())) {
                log.info("sms_aliyun_sent phone={}", PhoneMask.mask(phone));
                return SmsSendResult.ok();
            }
            String errorCode = response != null && response.getBody() != null ? response.getBody().getCode() : "EMPTY_RESPONSE";
            String message = response != null && response.getBody() != null ? response.getBody().getMessage() : "短信网关响应为空";
            log.warn("sms_aliyun_failed phone={} code={} message={}", PhoneMask.mask(phone), errorCode, message);
            return SmsSendResult.fail(errorCode, message);
        } catch (Exception exception) {
            log.warn("sms_aliyun_exception phone={} message={}", PhoneMask.mask(phone), exception.getMessage());
            return SmsSendResult.fail("EXCEPTION", exception.getMessage());
        }
    }
}
