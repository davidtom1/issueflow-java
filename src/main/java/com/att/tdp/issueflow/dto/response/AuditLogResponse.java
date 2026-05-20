package com.att.tdp.issueflow.dto.response;

import com.att.tdp.issueflow.entity.enums.AuditAction;
import com.att.tdp.issueflow.entity.enums.AuditActorType;
import com.att.tdp.issueflow.entity.enums.EntityType;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record AuditLogResponse(
        Long id,
        @JsonProperty("actor")
        AuditActorType actorType,
        @JsonProperty("performedBy")
        Long actorUserId,
        AuditAction action,
        EntityType entityType,
        Long entityId,
        Long projectId,
        Long ticketId,
        String oldValue,
        String newValue,
        @JsonProperty("timestamp")
        Instant createdAt
) {
}
