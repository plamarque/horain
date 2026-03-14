package com.horain.dto;

/**
 * Summary of activity type usage for a project (code, label, count).
 * Used when listing projects to show top activity types per project.
 */
public record ProjectActivityTypeSummaryDto(String code, String label, long count) {}
