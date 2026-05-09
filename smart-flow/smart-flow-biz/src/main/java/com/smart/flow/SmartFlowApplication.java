package com.smart.flow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Smart Flow service bootstrap.
 *
 * <p>This service hosts the lightweight workflow engine of the Smart platform. It wraps Flowable
 * as the underlying BPMN runtime, but exposes its own {@code FlowChart} domain model so that
 * front-end designers and business code never need to touch raw BPMN XML.
 */
@SpringBootApplication
@EnableDiscoveryClient
public class SmartFlowApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartFlowApplication.class, args);
    }
}
