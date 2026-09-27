package com.glint.server.biz.metro;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(GaodeProperties.class)
public class MetroConfiguration {
}
