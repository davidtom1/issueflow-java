package com.att.tdp.issueflow.dto.response;

import java.time.Instant;
import java.util.List;

public record ErrorResponse(
        int status,
        String error,
        String message,
        String path,
        Instant timestamp,
        List<ValidationErrorDetail> details
) {

    public static record ValidationErrorDetail(
            String field,
            String reason ) {}
}
