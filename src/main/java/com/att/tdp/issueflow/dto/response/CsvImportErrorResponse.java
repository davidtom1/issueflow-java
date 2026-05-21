package com.att.tdp.issueflow.dto.response;

public record CsvImportErrorResponse(
        int row,
        String reason
) {
}
