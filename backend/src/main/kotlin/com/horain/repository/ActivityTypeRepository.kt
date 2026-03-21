package com.horain.repository

import com.horain.model.ActivityType
import org.springframework.data.jpa.repository.JpaRepository

/**
 * JPA repository for activity types (natures with TJM).
 */
interface ActivityTypeRepository : JpaRepository<ActivityType, String> {

    fun findAllByOrderByCodeAsc(): List<ActivityType>
}
