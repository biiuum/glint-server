package com.glint.server.biz.sms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "glint.sms.aliyun", name = "enabled", havingValue = "false", matchIfMissing = true)
public class DevSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(DevSmsSender.class);
    private final SmsProperties properties;

    public DevSmsSender(SmsProperties properties) {
        this.properties = properties;
    }

    @Override
    public SmsSendResult sendCode(String phone, String code) {
        if (properties.isDevLogCode()) {
            log.info("sms_dev_sent phone={} code={}", PhoneMask.mask(phone), code);
        } else {
            log.info("sms_dev_sent phone={}", PhoneMask.mask(phone));
        }
        return SmsSendResult.ok();
    }
}
