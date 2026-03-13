package com.horain.model;

import jakarta.persistence.*;

/**
 * Activity nature (e.g. DEV, AI, MARK) with daily rate (TJM, 8h).
 * Referenced optionally by time_logs.
 */
@Entity
@Table(name = "activity_types")
public class ActivityType {

    @Id
    @Column(name = "code", length = 50)
    private String code;

    @Column(nullable = false, length = 255)
    private String label;

    @Column(name = "daily_rate_cents", nullable = false)
    private Integer dailyRateCents;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public Integer getDailyRateCents() {
        return dailyRateCents;
    }

    public void setDailyRateCents(Integer dailyRateCents) {
        this.dailyRateCents = dailyRateCents;
    }
}
