package com.horain

import com.horain.llm.LlmProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

/**
 * Horain backend application.
 * Provides sync API, projects, time logs, and chat endpoints.
 */
@SpringBootApplication
@EnableConfigurationProperties(LlmProperties::class)
class HorainApplication

fun main(args: Array<String>) {
    runApplication<HorainApplication>(*args)
}
