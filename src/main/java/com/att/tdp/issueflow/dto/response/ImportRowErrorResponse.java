package com.att.tdp.issueflow.dto.response;

public record ImportRowErrorResponse(
        int row,
        String reason
) {
}
