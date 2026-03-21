package com.horain.dto

/**
 * DTO for activity type (nature with daily rate).
 */
class ActivityTypeDto {
    var code: String? = null
    var label: String? = null
    var dailyRateCents: Int? = null
    var description: String? = null

    fun code(code: String?) = apply { this.code = code }
    fun label(label: String?) = apply { this.label = label }
    fun dailyRateCents(dailyRateCents: Int?) = apply { this.dailyRateCents = dailyRateCents }
    fun description(description: String?) = apply { this.description = description }

    companion object {
        @JvmStatic
        fun builder() = ActivityTypeDto()
    }
}
