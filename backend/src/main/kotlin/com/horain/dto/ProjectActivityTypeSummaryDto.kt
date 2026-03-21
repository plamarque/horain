package com.horain.dto

import kotlin.jvm.JvmRecord

/**
 * Summary of activity type usage for a project (code, label, count).
 * Used when listing projects to show top activity types per project.
 */
@JvmRecord
data class ProjectActivityTypeSummaryDto(
    val code: String,
    val label: String,
    val count: Long
)
