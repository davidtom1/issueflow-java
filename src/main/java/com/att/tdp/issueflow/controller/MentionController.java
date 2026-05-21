package com.att.tdp.issueflow.controller;
import com.att.tdp.issueflow.dto.response.CommentResponse;
import com.att.tdp.issueflow.dto.response.PagedResponse;
import com.att.tdp.issueflow.service.MentionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
@RestController
@RequestMapping("/users/{userId}/mentions")
@RequiredArgsConstructor
public class MentionController {
    
    private final MentionService mentionService;
    @GetMapping
    public ResponseEntity<PagedResponse<CommentResponse>> getMentionsForUser(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return ResponseEntity.ok(mentionService.getMentionsForUser(userId, page, pageSize));
    }
        
}
