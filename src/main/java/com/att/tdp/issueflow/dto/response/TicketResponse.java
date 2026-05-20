package com.att.tdp.issueflow.dto.response;

import com.att.tdp.issueflow.entity.enums.TicketPriority;
import com.att.tdp.issueflow.entity.enums.TicketStatus;
import com.att.tdp.issueflow.entity.enums.TicketType;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record TicketResponse(
        Long id,
        Long projectId,
        String title,
        String description,
        TicketStatus status,
        TicketPriority priority,
        TicketType type,
        Long assigneeId,
        String assigneeUsername,
        Instant dueDate,
        @JsonProperty("isOverdue")
        boolean overdue,
        Instant lastAutoEscalatedAt,
        Long version,
        Instant createdAt,
        Instant updatedAt
) {
}
