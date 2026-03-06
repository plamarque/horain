package com.horain.repository;

import com.horain.model.TimeLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * JPA repository for time logs.
 */
public interface TimeLogRepository extends JpaRepository<TimeLog, UUID> {

    List<TimeLog> findByUpdatedAtAfter(Instant after);
}
