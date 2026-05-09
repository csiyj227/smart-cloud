package com.smart.nl2sql;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableDiscoveryClient
@EnableAsync
public class SmartNl2sqlApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartNl2sqlApplication.class, args);
    }
}