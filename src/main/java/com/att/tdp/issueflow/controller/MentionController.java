package com.att.tdp.issueflow.controller;
import com.att.tdp.issueflow.dto.response.CommentResponse;
import com.att.tdp.issueflow.service.MentionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
@RestController
@RequestMapping("/users/{userId}/mentions")
@RequiredArgsConstructor
public class MentionController {
    
    private final MentionService mentionService;
    @GetMapping
    public ResponseEntity<List<CommentResponse>> getMentionsForUser(@PathVariable Long userId){
        List<CommentResponse> mentions = mentionService.getMentionsForUser(userId);
        return ResponseEntity.ok(mentions);
    
    }
        
}
