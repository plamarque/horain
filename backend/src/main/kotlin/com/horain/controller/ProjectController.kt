package com.horain.controller

import com.horain.dto.ProjectDto
import com.horain.service.ProjectService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Project API controller.
 */
@RestController
@RequestMapping("/projects")
class ProjectController(
    private val projectService: ProjectService
) {

    @PostMapping
    fun create(@RequestBody dto: ProjectDto): ResponseEntity<ProjectDto> {
        val created = projectService.create(dto)
        return ResponseEntity.status(HttpStatus.CREATED).body(created)
    }

    @GetMapping
    fun list(): ResponseEntity<List<ProjectDto>> =
        ResponseEntity.ok(projectService.findAll())

    @PatchMapping("/{id}")
    fun update(@PathVariable id: UUID, @RequestBody patch: Map<String, Any?>): ResponseEntity<ProjectDto> {
        val dto = ProjectDto.builder().id(id).build()
        if (patch.containsKey("name")) {
            dto.name = patch["name"]?.toString()?.trim()
        }
        if (patch.containsKey("description")) {
            dto.description = patch["description"]?.toString()
        }
        if (patch.containsKey("billable")) {
            val v = patch["billable"]
            dto.billable = when (v) {
                is Boolean -> v
                else -> v.toString().toBoolean()
            }
        }
        val updated = projectService.update(id, dto)
        return ResponseEntity.ok(updated)
    }
}
