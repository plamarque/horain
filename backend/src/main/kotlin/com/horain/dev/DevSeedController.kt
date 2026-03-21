package com.horain.dev

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

/**
 * Dev-only endpoint to load fictional seed data.
 * Disabled when horain.dev.seed-enabled is false (e.g. production).
 * Optional body: { "fixedToday": "2025-03-10" } for deterministic evals.
 */
@RestController
@RequestMapping("/dev")
class DevSeedController(
    private val devSeedService: DevSeedService
) {

    @Value("\${horain.dev.seed-enabled:false}")
    private var seedEnabled: Boolean = false

    @PostMapping("/seed")
    fun loadSeed(@RequestBody(required = false) body: SeedRequest?): ResponseEntity<*> {
        if (!seedEnabled) {
            return ResponseEntity.notFound().build<Any>()
        }
        val fixedToday = parseFixedToday(body?.fixedToday)
        val result = devSeedService.loadSeed(fixedToday)
        return ResponseEntity.ok(result)
    }

    @PostMapping("/seed/reset")
    fun resetSeed(@RequestBody(required = false) body: SeedRequest?): ResponseEntity<*> {
        if (!seedEnabled) {
            return ResponseEntity.notFound().build<Any>()
        }
        val fixedToday = parseFixedToday(body?.fixedToday)
        val result = devSeedService.resetAndLoadSeed(fixedToday)
        return ResponseEntity.ok(result)
    }

    private fun parseFixedToday(fixedToday: String?): LocalDate? {
        if (fixedToday.isNullOrBlank()) return null
        return try {
            LocalDate.parse(fixedToday)
        } catch (_: Exception) {
            null
        }
    }

    data class SeedRequest(val fixedToday: String?)
}
