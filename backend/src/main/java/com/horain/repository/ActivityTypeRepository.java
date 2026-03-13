package com.horain.repository;

import com.horain.model.ActivityType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * JPA repository for activity types (natures with TJM).
 */
public interface ActivityTypeRepository extends JpaRepository<ActivityType, String> {

    List<ActivityType> findAllByOrderByCodeAsc();
}
