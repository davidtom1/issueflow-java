package com.att.tdp.issueflow.security;

import com.att.tdp.issueflow.entity.enums.UserRole;

public record AuthenticatedUser(
        Long id,
        String username,
        UserRole role
) {
}
