package com.horain.dto;

/**
 * DTO for activity type (nature with daily rate).
 */
public class ActivityTypeDto {

    private String code;
    private String label;
    private Integer dailyRateCents;

    public static ActivityTypeDto builder() {
        return new ActivityTypeDto();
    }

    public ActivityTypeDto code(String code) {
        this.code = code;
        return this;
    }

    public ActivityTypeDto label(String label) {
        this.label = label;
        return this;
    }

    public ActivityTypeDto dailyRateCents(Integer dailyRateCents) {
        this.dailyRateCents = dailyRateCents;
        return this;
    }

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
