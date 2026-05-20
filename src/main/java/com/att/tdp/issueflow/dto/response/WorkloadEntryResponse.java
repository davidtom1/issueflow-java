package com.att.tdp.issueflow.dto.response;

public record WorkloadEntryResponse(
        Long userId,
        String username,
        long openTicketCount
) {
}
