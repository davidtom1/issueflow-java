package com.att.tdp.issueflow.dto.response;

import java.util.List;

public record PagedResponse<T>(
        List<T> data,
        long total,
        int page
) {
}