package com.smart.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Smart AI service bootstrap.
 *
 * <p>This service hosts the AI assistant capabilities of the Smart platform, including
 * LLM model management, intelligent conversations, RAG knowledge retrieval,
 * Agent orchestration and MCP tool integration.
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableAsync
public class SmartAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartAiApplication.class, args);
    }
}
