package com.horain.repository;

import com.horain.model.TimeLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * JPA repository for time logs.
 */
public interface TimeLogRepository extends JpaRepository<TimeLog, UUID> {

    List<TimeLog> findByUpdatedAtAfter(Instant after);

    List<TimeLog> findTop50ByOrderByLoggedAtDesc();

    List<TimeLog> findByLoggedAtBetweenOrderByLoggedAtDesc(Instant start, Instant end);

    List<TimeLog> findByProjectIdAndLoggedAtBetweenOrderByLoggedAtDesc(UUID projectId, Instant start, Instant end);

    @Query("SELECT COALESCE(SUM(t.durationMinutes), 0) FROM TimeLog t WHERE t.loggedAt BETWEEN :start AND :end")
    Integer sumDurationMinutesByLoggedAtBetween(@Param("start") Instant start, @Param("end") Instant end);

    @Query("SELECT COALESCE(SUM(t.durationMinutes), 0) FROM TimeLog t WHERE t.projectId = :projectId AND t.loggedAt BETWEEN :start AND :end")
    Integer sumDurationMinutesByProjectAndLoggedAtBetween(
            @Param("projectId") UUID projectId,
            @Param("start") Instant start,
            @Param("end") Instant end);
}
