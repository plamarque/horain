package com.horain.flyway

import org.flywaydb.core.api.migration.JavaMigration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Registers Flyway Java migrations as beans so Spring Boot passes them to Flyway
 * (see FlywayAutoConfiguration#configureJavaMigrations). SQL files stay under db/migration/{vendor}.
 */
@Configuration
class FlywayJavaMigrationsConfig {

    @Bean
    fun v15DropLegacyH2ActivityTypeCheckConstraints(): JavaMigration =
        V15__DropLegacyH2ActivityTypeCheckConstraints()
}
