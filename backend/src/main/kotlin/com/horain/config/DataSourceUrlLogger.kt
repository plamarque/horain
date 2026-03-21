package com.horain.config

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component

/**
 * Logs the resolved datasource URL at startup (password redacted) for debugging
 * Cloud Run / Supabase connection issues. Only active when profile "postgres" is enabled.
 */
@Component
@Profile("postgres")
class DataSourceUrlLogger(
    private val environment: Environment
) : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        val url = environment.getProperty("spring.datasource.url", "")
        val masked = url.replace(Regex("([?&]password=)[^&]*"), "$1***")
        log.info("Datasource URL (postgres profile): {}", if (masked.isEmpty()) "(not set)" else masked)
    }

    companion object {
        private val log = LoggerFactory.getLogger(DataSourceUrlLogger::class.java)
    }
}
