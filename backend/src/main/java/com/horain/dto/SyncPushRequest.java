package com.horain.dto;

import java.util.List;

/**
 * Request body for sync push endpoint.
 */
public class SyncPushRequest {

    private List<SyncOperationDto> operations;

    public List<SyncOperationDto> getOperations() { return operations; }
    public void setOperations(List<SyncOperationDto> operations) { this.operations = operations; }
}
