package com.att.tdp.issueflow.dto.response;

import java.time.Instant;

public record ProjectResponse(
        Long id,
        String name,
        String description,
        Long ownerId,
        String ownerUsername,
        boolean deleted,
        Instant deletedAt,
        Long deletedById,
        Long version,
        Instant createdAt,
        Instant updatedAt
) {
}
