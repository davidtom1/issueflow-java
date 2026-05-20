package com.att.tdp.issueflow.dto.response;

import java.time.Instant;
import java.util.List;

public record CommentResponse(
        Long id,
        Long ticketId,
        Long authorId,
        String authorUsername,
        String content,
        List<MentionedUserResponse> mentionedUsers,
        Long version,
        Instant createdAt,
        Instant updatedAt
) {
}
