package com.horain.dto;

import java.util.Map;

/**
 * DTO for a sync operation in push batch.
 */
public class SyncOperationDto {

    private String entityType;
    private String entityId;
    private String operation;
    private Map<String, Object> payload;

    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }
    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }
    public Map<String, Object> getPayload() { return payload; }
    public void setPayload(Map<String, Object> payload) { this.payload = payload; }
}
