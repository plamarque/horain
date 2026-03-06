package com.horain;

import com.horain.llm.LlmProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Horain backend application.
 * Provides sync API, projects, time logs, and chat endpoints.
 */
@SpringBootApplication
@EnableConfigurationProperties(LlmProperties.class)
public class HorainApplication {

    public static void main(String[] args) {
        SpringApplication.run(HorainApplication.class, args);
    }
}
