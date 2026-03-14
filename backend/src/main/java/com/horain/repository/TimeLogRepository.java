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

    List<TimeLog> findTop50ByOrderByLoggedAtDesc();

    @Query("SELECT t FROM TimeLog t WHERE t.loggedAt >= :start AND t.loggedAt < :end ORDER BY t.loggedAt DESC")
    List<TimeLog> findByLoggedAtBetweenOrderByLoggedAtDesc(@Param("start") Instant start, @Param("end") Instant end);

    @Query("SELECT t FROM TimeLog t WHERE t.projectId = :projectId AND t.loggedAt >= :start AND t.loggedAt < :end ORDER BY t.loggedAt DESC")
    List<TimeLog> findByProjectIdAndLoggedAtBetweenOrderByLoggedAtDesc(
            @Param("projectId") UUID projectId,
            @Param("start") Instant start,
            @Param("end") Instant end);

    long countByProjectId(UUID projectId);

    /** Period is [start, end) (end exclusive) so day boundaries align with chart grouping by day. */
    @Query("SELECT COALESCE(SUM(t.durationMinutes), 0) FROM TimeLog t WHERE t.loggedAt >= :start AND t.loggedAt < :end")
    Integer sumDurationMinutesByLoggedAtBetween(@Param("start") Instant start, @Param("end") Instant end);

    @Query("SELECT COALESCE(SUM(t.durationMinutes), 0) FROM TimeLog t WHERE t.projectId = :projectId AND t.loggedAt >= :start AND t.loggedAt < :end")
    Integer sumDurationMinutesByProjectAndLoggedAtBetween(
            @Param("projectId") UUID projectId,
            @Param("start") Instant start,
            @Param("end") Instant end);

    @Query("SELECT COALESCE(SUM(t.durationMinutes), 0) FROM TimeLog t WHERE t.loggedAt >= :start AND t.loggedAt < :end AND t.billable = :billable")
    Integer sumDurationMinutesByLoggedAtBetweenAndBillable(
            @Param("start") Instant start,
            @Param("end") Instant end,
            @Param("billable") boolean billable);

    /**
     * Sum revenue in cents per project for billable time logs that have an activity type (TJM).
     * Formula per entry: value_cents = (duration_minutes * daily_rate_cents) / 480 (no extra * 100).
     * Returns one row per projectId with non-zero revenue. Uses JPQL for dialect compatibility (H2 and PostgreSQL).
     */
    @Query("SELECT t.projectId, SUM((t.durationMinutes * at.dailyRateCents) / 480.0) FROM TimeLog t JOIN t.activityType at WHERE t.billable = true AND t.activityTypeCode IS NOT NULL GROUP BY t.projectId")
    List<Object[]> sumRevenueCentsByProject();
}
