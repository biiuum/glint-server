package com.glint.server.web.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** UAT/Prod 缺少外部依赖配置时立即停止启动，避免运行到接口调用才暴露问题。 */
@Component
@Profile({"uat", "prod"})
public class ReleaseConfigurationValidator implements ApplicationRunner {

    private final Environment environment;

    public ReleaseConfigurationValidator(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> missing = new ArrayList<>();
        require("oss.access-key-id", missing);
        require("oss.access-key-secret", missing);
        require("oss.bucket", missing);
        require("glint.message-encryption-key", missing);
        require("glint.metro.sync-admin-key", missing);
        if (environment.getProperty("glint.gaode.sync-on-startup", Boolean.class, true)) {
            require("glint.gaode.web-key", missing);
        }
        if (!Boolean.parseBoolean(environment.getProperty("glint.sms.aliyun.enabled", "false"))) {
            missing.add("glint.sms.aliyun.enabled");
        }
        require("glint.sms.aliyun.access-key", missing);
        require("glint.sms.aliyun.secret", missing);
        require("glint.sms.aliyun.sign-name", missing);
        require("glint.sms.aliyun.template-code", missing);
        if (!missing.isEmpty()) {
            throw new IllegalStateException("UAT/Prod 缺少必需配置: " + String.join(", ", missing));
        }
    }

    private void require(String key, List<String> missing) {
        String value = environment.getProperty(key);
        if (value == null || value.isBlank() || value.startsWith("__")) {
            missing.add(key);
        }
    }
}
