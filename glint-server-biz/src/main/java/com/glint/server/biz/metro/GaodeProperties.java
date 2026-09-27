package com.glint.server.biz.metro;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "glint.gaode")
public class GaodeProperties {

    private String webKey = "";
    private String subwayBaseUrl = "https://map.amap.com/service/subway";
    private boolean syncOnStartup = true;

    public String getWebKey() {
        return webKey;
    }

    public void setWebKey(String webKey) {
        this.webKey = webKey;
    }

    public String getSubwayBaseUrl() {
        return subwayBaseUrl;
    }

    public void setSubwayBaseUrl(String subwayBaseUrl) {
        this.subwayBaseUrl = subwayBaseUrl;
    }

    public boolean isSyncOnStartup() {
        return syncOnStartup;
    }

    public void setSyncOnStartup(boolean syncOnStartup) {
        this.syncOnStartup = syncOnStartup;
    }
}
