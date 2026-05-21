package com.att.tdp.issueflow.dto.response;

import com.att.tdp.issueflow.entity.enums.TicketStatus;

public record TicketDependencyResponse(
        Long id,
        String title,
        TicketStatus status
) {
}