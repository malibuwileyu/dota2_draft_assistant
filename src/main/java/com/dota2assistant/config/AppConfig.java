package com.dota2assistant.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

/**
 * Main Spring configuration class.
 * Enables component scanning for all application packages.
 */
@Configuration
@ComponentScan(basePackages = "com.dota2assistant")
@PropertySource(value = "classpath:application.properties", ignoreResourceNotFound = true)
public class AppConfig {
    // Bean definitions will be added as needed
}

