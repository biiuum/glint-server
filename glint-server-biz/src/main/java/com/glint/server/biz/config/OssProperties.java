package com.glint.server.biz.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 阿里云 OSS 配置
 */
@ConfigurationProperties(prefix = "oss")
public class OssProperties {

    /** OSS Endpoint，如 oss-cn-beijing.aliyuncs.com */
    private String endpoint = "oss-cn-beijing.aliyuncs.com";

    /** AccessKey ID */
    private String accessKeyId;

    /** AccessKey Secret */
    private String accessKeySecret;

    /** Bucket 名称 */
    private String bucket;

    /** 上传预签名 URL 有效期（秒），默认 1 小时 */
    private int presignExpireSeconds = 3600;

    /** 读图预签名 URL 有效期（秒），默认 24 小时 */
    private int readPresignExpireSeconds = 86400;

    /** 自定义域名（可选，用于 CDN），不配置则使用默认 OSS 域名 */
    private String customDomain;

    /**
     * 读图是否走 GET 预签名 URL。
     * 私有桶 + CDN 未配好 HTTPS/公开读时须为 true；CDN 就绪后可设为 false 走无签名 CDN URL。
     */
    private boolean readUsePresign = false;

    /** OSS SDK 最大连接数，默认 200 */
    private int maxConnections = 200;

    /** OSS SDK 连接超时（毫秒），默认 5000 */
    private int connectionTimeout = 5000;

    /** OSS SDK Socket 读取超时（毫秒），默认 30000 */
    private int socketTimeout = 30000;

    /**
     * 是否读取 JVM 系统代理（http.proxyHost / socksProxyHost 等）。
     * 本地 dev 若开着系统代理但 Clash 等未启动，会导致 OSS 校验失败。
     */
    private boolean useSystemProxy = true;

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getAccessKeyId() {
        return accessKeyId;
    }

    public void setAccessKeyId(String accessKeyId) {
        this.accessKeyId = accessKeyId;
    }

    public String getAccessKeySecret() {
        return accessKeySecret;
    }

    public void setAccessKeySecret(String accessKeySecret) {
        this.accessKeySecret = accessKeySecret;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public int getPresignExpireSeconds() {
        return presignExpireSeconds;
    }

    public void setPresignExpireSeconds(int presignExpireSeconds) {
        this.presignExpireSeconds = presignExpireSeconds;
    }

    public int getReadPresignExpireSeconds() {
        return readPresignExpireSeconds;
    }

    public void setReadPresignExpireSeconds(int readPresignExpireSeconds) {
        this.readPresignExpireSeconds = readPresignExpireSeconds;
    }

    public String getCustomDomain() {
        return customDomain;
    }

    public void setCustomDomain(String customDomain) {
        this.customDomain = customDomain;
    }

    public boolean isReadUsePresign() {
        return readUsePresign;
    }

    public void setReadUsePresign(boolean readUsePresign) {
        this.readUsePresign = readUsePresign;
    }

    public int getMaxConnections() {
        return maxConnections;
    }

    public void setMaxConnections(int maxConnections) {
        this.maxConnections = maxConnections;
    }

    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public int getSocketTimeout() {
        return socketTimeout;
    }

    public void setSocketTimeout(int socketTimeout) {
        this.socketTimeout = socketTimeout;
    }

    public boolean isUseSystemProxy() {
        return useSystemProxy;
    }

    public void setUseSystemProxy(boolean useSystemProxy) {
        this.useSystemProxy = useSystemProxy;
    }
}
