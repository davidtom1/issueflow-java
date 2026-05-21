package com.att.tdp.issueflow.dto.response;

import java.util.List;

public record CsvImportResponse(
        int created,
        int failed,
        List<CsvImportErrorResponse> errors
) {
}
