package com.horain.dto;

import java.util.List;

/**
 * Response for sync pull endpoint.
 */
public class SyncPullResponse {

    private List<ProjectDto> projects;
    private List<TimeLogDto> timeLogs;

    public static SyncPullResponse builder() { return new SyncPullResponse(); }
    public SyncPullResponse projects(List<ProjectDto> projects) { this.projects = projects; return this; }
    public SyncPullResponse timeLogs(List<TimeLogDto> timeLogs) { this.timeLogs = timeLogs; return this; }
    public SyncPullResponse build() { return this; }

    public List<ProjectDto> getProjects() { return projects; }
    public List<TimeLogDto> getTimeLogs() { return timeLogs; }
}
