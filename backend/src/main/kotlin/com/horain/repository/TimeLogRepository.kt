package com.horain.repository

import com.horain.model.TimeLog
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

/**
 * JPA repository for time logs.
 */
interface TimeLogRepository : JpaRepository<TimeLog, UUID> {

    fun findTop50ByOrderByLoggedAtDesc(): List<TimeLog>

    @Query("SELECT t FROM TimeLog t WHERE t.loggedAt >= :start AND t.loggedAt < :end ORDER BY t.loggedAt DESC")
    fun findByLoggedAtBetweenOrderByLoggedAtDesc(
        @Param("start") start: Instant,
        @Param("end") end: Instant
    ): List<TimeLog>

    /** Same period as above, paginated (newest first). */
    @Query("SELECT t FROM TimeLog t WHERE t.loggedAt >= :start AND t.loggedAt < :end ORDER BY t.loggedAt DESC")
    fun findByLoggedAtBetweenOrderByLoggedAtDesc(
        @Param("start") start: Instant,
        @Param("end") end: Instant,
        pageable: Pageable
    ): List<TimeLog>

    @Query(
        "SELECT DISTINCT t.projectId FROM TimeLog t WHERE t.loggedAt >= :start AND t.loggedAt < :end"
    )
    fun findDistinctProjectIdsByLoggedAtBetween(
        @Param("start") start: Instant,
        @Param("end") end: Instant
    ): List<UUID>

    @Query(
        "SELECT t FROM TimeLog t WHERE t.projectId = :projectId AND t.loggedAt >= :start AND t.loggedAt < :end ORDER BY t.loggedAt DESC"
    )
    fun findByProjectIdAndLoggedAtBetweenOrderByLoggedAtDesc(
        @Param("projectId") projectId: UUID,
        @Param("start") start: Instant,
        @Param("end") end: Instant
    ): List<TimeLog>

    fun countByProjectId(projectId: UUID): Long

    /** Period is [start, end) (end exclusive) so day boundaries align with chart grouping by day. */
    @Query("SELECT COALESCE(SUM(t.durationMinutes), 0) FROM TimeLog t WHERE t.loggedAt >= :start AND t.loggedAt < :end")
    fun sumDurationMinutesByLoggedAtBetween(
        @Param("start") start: Instant,
        @Param("end") end: Instant
    ): Int?

    @Query(
        "SELECT COALESCE(SUM(t.durationMinutes), 0) FROM TimeLog t WHERE t.projectId = :projectId AND t.loggedAt >= :start AND t.loggedAt < :end"
    )
    fun sumDurationMinutesByProjectAndLoggedAtBetween(
        @Param("projectId") projectId: UUID,
        @Param("start") start: Instant,
        @Param("end") end: Instant
    ): Int?

    @Query(
        "SELECT COALESCE(SUM(t.durationMinutes), 0) FROM TimeLog t WHERE t.loggedAt >= :start AND t.loggedAt < :end AND t.billable = :billable"
    )
    fun sumDurationMinutesByLoggedAtBetweenAndBillable(
        @Param("start") start: Instant,
        @Param("end") end: Instant,
        @Param("billable") billable: Boolean
    ): Int?

    /**
     * Sum revenue in cents per project for billable time logs that have an activity type (TJM).
     * Formula per entry: value_cents = (duration_minutes * daily_rate_cents) / 480 (no extra * 100).
     * Returns one row per projectId with non-zero revenue. Uses JPQL for dialect compatibility (H2 and PostgreSQL).
     */
    @Query(
        "SELECT t.projectId, SUM((t.durationMinutes * at.dailyRateCents) / 480.0) FROM TimeLog t JOIN t.activityType at WHERE t.billable = true AND t.activityTypeCode IS NOT NULL GROUP BY t.projectId"
    )
    fun sumRevenueCentsByProject(): List<Array<Any>>

    @Query(
        "SELECT t.projectId, SUM((t.durationMinutes * at.dailyRateCents) / 480.0) FROM TimeLog t JOIN t.activityType at " +
            "WHERE t.billable = true AND t.activityTypeCode IS NOT NULL AND t.loggedAt >= :start AND t.loggedAt < :end GROUP BY t.projectId"
    )
    fun sumRevenueCentsByProjectForPeriod(
        @Param("start") start: Instant,
        @Param("end") end: Instant
    ): List<Array<Any>>

    /** Count time logs per project. Returns (projectId, count). Projects with zero logs are not in the result. */
    @Query("SELECT t.projectId, COUNT(t) FROM TimeLog t GROUP BY t.projectId")
    fun countByProjectId(): List<Array<Any>>

    @Query(
        "SELECT t.projectId, COUNT(t) FROM TimeLog t WHERE t.loggedAt >= :start AND t.loggedAt < :end GROUP BY t.projectId"
    )
    fun countLogsByProjectForPeriod(
        @Param("start") start: Instant,
        @Param("end") end: Instant
    ): List<Array<Any>>

    /**
     * Count time logs per project and activity type (only entries with a type).
     * Returns (projectId, activityTypeCode, label, count) for building top-activity-types per project.
     */
    @Query(
        "SELECT t.projectId, t.activityTypeCode, at.label, COUNT(t) FROM TimeLog t JOIN t.activityType at WHERE t.activityTypeCode IS NOT NULL GROUP BY t.projectId, t.activityTypeCode, at.label"
    )
    fun countByProjectIdAndActivityType(): List<Array<Any>>

    @Query(
        "SELECT t.projectId, t.activityTypeCode, at.label, COUNT(t) FROM TimeLog t JOIN t.activityType at " +
            "WHERE t.activityTypeCode IS NOT NULL AND t.loggedAt >= :start AND t.loggedAt < :end " +
            "GROUP BY t.projectId, t.activityTypeCode, at.label"
    )
    fun countByProjectIdAndActivityTypeForPeriod(
        @Param("start") start: Instant,
        @Param("end") end: Instant
    ): List<Array<Any>>

    /**
     * Search time logs by keyword: matches in note (case-insensitive contains) or project name.
     * Returns results ordered by loggedAt DESC, limited by pageable.
     */
    @Query(
        "SELECT t FROM TimeLog t JOIN t.project p WHERE (LOWER(COALESCE(t.note, '')) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%'))) ORDER BY t.loggedAt DESC"
    )
    fun searchByKeyword(@Param("query") query: String, pageable: Pageable): List<TimeLog>
}
