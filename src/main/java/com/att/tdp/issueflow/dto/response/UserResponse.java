package com.att.tdp.issueflow.dto.response;

import com.att.tdp.issueflow.entity.enums.UserRole;
import java.time.Instant;

public record UserResponse(
        Long id,
        String username,
        String email,
        String fullName,
        UserRole role,
        Instant createdAt,
        Instant updatedAt
) {
}
