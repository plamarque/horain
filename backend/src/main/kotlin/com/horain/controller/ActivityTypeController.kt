package com.horain.controller

import com.horain.dto.ActivityTypeDto
import com.horain.service.ActivityTypeService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Activity types (natures + TJM) API. Used by frontend and by MCP tools.
 */
@RestController
@RequestMapping("/activity-types")
class ActivityTypeController(
    private val activityTypeService: ActivityTypeService
) {

    @GetMapping
    fun list(): ResponseEntity<List<ActivityTypeDto>> =
        ResponseEntity.ok(activityTypeService.findAll())

    @PostMapping
    fun create(@RequestBody dto: ActivityTypeDto): ResponseEntity<ActivityTypeDto> {
        val created = activityTypeService.create(dto)
        return ResponseEntity.status(HttpStatus.CREATED).body(created)
    }

    @PatchMapping("/{code}")
    fun update(
        @PathVariable code: String,
        @RequestBody patch: Map<String, Any?>
    ): ResponseEntity<ActivityTypeDto> {
        val dto = ActivityTypeDto()
        if (patch.containsKey("label")) {
            dto.label = patch["label"]?.toString()
        }
        if (patch.containsKey("dailyRateCents")) {
            val v = patch["dailyRateCents"]
            dto.dailyRateCents = when (v) {
                is Number -> v.toInt()
                else -> v.toString().toInt()
            }
        }
        val updated = activityTypeService.update(code, dto)
        return ResponseEntity.ok(updated)
    }

    @DeleteMapping("/{code}")
    fun delete(@PathVariable code: String): ResponseEntity<Void> {
        activityTypeService.deleteByCode(code)
        return ResponseEntity.noContent().build()
    }
}
