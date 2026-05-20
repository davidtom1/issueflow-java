package com.att.tdp.issueflow.dto.response;

import java.time.Instant;

public record AttachmentResponse(
        Long id,
        Long ticketId,
        String filename,
        String contentType,
        Long sizeBytes,
        Long uploadedById,
        Instant uploadedAt
) {
}
