package com.horain.dto;

/**
 * Response for sync push endpoint.
 */
public class SyncPushResponse {

    private boolean success;
    private int processedCount;

    public static SyncPushResponse builder() { return new SyncPushResponse(); }
    public SyncPushResponse success(boolean success) { this.success = success; return this; }
    public SyncPushResponse processedCount(int processedCount) { this.processedCount = processedCount; return this; }
    public SyncPushResponse build() { return this; }

    public boolean isSuccess() { return success; }
    public int getProcessedCount() { return processedCount; }
}
