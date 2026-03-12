package com.horain.repository;

import com.horain.model.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * JPA repository for projects.
 */
public interface ProjectRepository extends JpaRepository<Project, UUID> {

    /**
     * Fuzzy search by project name (case-insensitive contains).
     */
    List<Project> findByNameContainingIgnoreCase(String name);
}
