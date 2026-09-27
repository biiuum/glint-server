package com.glint.server.biz.sms;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@ConfigurationProperties(prefix = "glint.sms")
public class SmsProperties {

    private Aliyun aliyun = new Aliyun();
    private String whitelistPhones = "";
    private String devFixedCode = "";
    private boolean devLogCode = false;

    public Aliyun getAliyun() {
        return aliyun;
    }

    public void setAliyun(Aliyun aliyun) {
        this.aliyun = aliyun;
    }

    public String getWhitelistPhones() {
        return whitelistPhones;
    }

    public void setWhitelistPhones(String whitelistPhones) {
        this.whitelistPhones = whitelistPhones;
    }

    public String getDevFixedCode() {
        return devFixedCode;
    }

    public void setDevFixedCode(String devFixedCode) {
        this.devFixedCode = devFixedCode;
    }

    public boolean isDevLogCode() {
        return devLogCode;
    }

    public void setDevLogCode(boolean devLogCode) {
        this.devLogCode = devLogCode;
    }

    public Set<String> whitelist() {
        if (!StringUtils.hasText(whitelistPhones)) {
            return Set.of();
        }
        return Arrays.stream(whitelistPhones.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toSet());
    }

    public static class Aliyun {
        private boolean enabled = false;
        private String accessKey = "";
        private String secret = "";
        private String signName = "";
        private String templateCode = "";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getAccessKey() {
            return accessKey;
        }

        public void setAccessKey(String accessKey) {
            this.accessKey = accessKey;
        }

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public String getSignName() {
            return signName;
        }

        public void setSignName(String signName) {
            this.signName = signName;
        }

        public String getTemplateCode() {
            return templateCode;
        }

        public void setTemplateCode(String templateCode) {
            this.templateCode = templateCode;
        }

        public boolean isComplete() {
            return StringUtils.hasText(accessKey)
                    && StringUtils.hasText(secret)
                    && StringUtils.hasText(signName)
                    && StringUtils.hasText(templateCode);
        }

        public String missingFieldsDescription() {
            List<String> missing = new ArrayList<>();
            if (!StringUtils.hasText(accessKey)) missing.add("glint.sms.aliyun.access-key");
            if (!StringUtils.hasText(secret)) missing.add("glint.sms.aliyun.secret");
            if (!StringUtils.hasText(signName)) missing.add("glint.sms.aliyun.sign-name");
            if (!StringUtils.hasText(templateCode)) missing.add("glint.sms.aliyun.template-code");
            return String.join(", ", missing);
        }
    }
}
