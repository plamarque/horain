package com.horain.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * Activity nature (e.g. DEV, AI, MARK) with daily rate (TJM, 8h).
 * Referenced optionally by time_logs.
 */
@Entity
@Table(name = "activity_types")
class ActivityType {
    @Id
    @Column(name = "code", length = 50)
    var code: String? = null

    @Column(nullable = false, length = 255)
    var label: String? = null

    @Column(name = "daily_rate_cents", nullable = false)
    var dailyRateCents: Int? = null

    @Column(length = 2000)
    var description: String? = null
}
