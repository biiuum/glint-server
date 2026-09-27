package com.glint.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class GlintServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(GlintServerApplication.class, args);
    }
}
