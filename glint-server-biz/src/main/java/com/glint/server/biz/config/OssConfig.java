package com.glint.server.biz.config;

import com.aliyun.oss.ClientBuilderConfiguration;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 阿里云 OSS 客户端配置
 */
@Configuration
@EnableConfigurationProperties(OssProperties.class)
@ConditionalOnExpression("!'${oss.access-key-id:}'.trim().isEmpty()")
public class OssConfig {

    @Bean(destroyMethod = "shutdown")
    public OSS ossClient(OssProperties properties) {
        ClientBuilderConfiguration config = new ClientBuilderConfiguration();
        config.setMaxConnections(properties.getMaxConnections());
        config.setConnectionTimeout(properties.getConnectionTimeout());
        config.setSocketTimeout(properties.getSocketTimeout());
        config.setConnectionRequestTimeout(3000);
        config.setUseSystemPropertyValues(properties.isUseSystemProxy());
        return new OSSClientBuilder().build(
                properties.getEndpoint(),
                properties.getAccessKeyId(),
                properties.getAccessKeySecret(),
                config
        );
    }
}
