package com.horain.repository

import com.horain.model.Project
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/**
 * JPA repository for projects.
 */
interface ProjectRepository : JpaRepository<Project, UUID> {

    /**
     * Fuzzy search by project name (case-insensitive contains).
     */
    fun findByNameContainingIgnoreCase(name: String): List<Project>
}
