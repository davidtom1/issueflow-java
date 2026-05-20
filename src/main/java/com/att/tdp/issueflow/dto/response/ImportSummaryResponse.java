package com.att.tdp.issueflow.dto.response;

import java.util.List;

public record ImportSummaryResponse(
        int created,
        int failed,
        List<ImportRowErrorResponse> errors
) {
}
