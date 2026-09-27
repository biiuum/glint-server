package com.glint.server.biz.sms;

import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.teaopenapi.models.Config;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SmsProperties.class)
public class SmsConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "glint.sms.aliyun", name = "enabled", havingValue = "true")
    public Client aliyunSmsClient(SmsProperties properties) throws Exception {
        SmsProperties.Aliyun aliyun = properties.getAliyun();
        if (!aliyun.isComplete()) {
            throw new IllegalStateException("阿里云短信配置不完整，缺失: " + aliyun.missingFieldsDescription());
        }
        Config config = new Config()
                .setAccessKeyId(aliyun.getAccessKey())
                .setAccessKeySecret(aliyun.getSecret())
                .setEndpoint("dysmsapi.aliyuncs.com")
                .setConnectTimeout(5000)
                .setReadTimeout(10000);
        return new Client(config);
    }
}
