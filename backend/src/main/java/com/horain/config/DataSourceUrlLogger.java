package com.horain.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Logs the resolved datasource URL at startup (password redacted) for debugging
 * Cloud Run / Supabase connection issues. Only active when profile "postgres" is enabled.
 */
@Component
@Profile("postgres")
public class DataSourceUrlLogger implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSourceUrlLogger.class);

    private final Environment environment;

    public DataSourceUrlLogger(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        String url = environment.getProperty("spring.datasource.url", "");
        String masked = url.replaceAll("([?&]password=)[^&]*", "$1***");
        log.info("Datasource URL (postgres profile): {}", masked.isEmpty() ? "(not set)" : masked);
    }
}
