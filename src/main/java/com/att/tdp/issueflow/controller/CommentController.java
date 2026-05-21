package com.att.tdp.issueflow.controller;
import com.att.tdp.issueflow.dto.request.CreateCommentRequest;
import com.att.tdp.issueflow.dto.request.UpdateCommentRequest;
import com.att.tdp.issueflow.dto.response.CommentResponse;
import com.att.tdp.issueflow.service.CommentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
@RestController
@RequestMapping("/tickets/{ticketId}/comments")
@RequiredArgsConstructor
public class CommentController {
    private final CommentService commentService;

    @GetMapping
    public ResponseEntity<List<CommentResponse>> getCommentsForTicket(@PathVariable Long ticketId){
        List<CommentResponse> comments = commentService.getCommentsForTicket(ticketId);
        return ResponseEntity.ok(comments);
    }
    @PostMapping
        public ResponseEntity<CommentResponse> addComment(
                @PathVariable Long ticketId,
                @Valid @RequestBody CreateCommentRequest request
        ){
        CommentResponse createdComment = commentService.addComment(ticketId, request);
        return ResponseEntity.ok(createdComment);
        }
    
    @PatchMapping("/{commentId}")
    public ResponseEntity<CommentResponse> updateComment(
            @PathVariable Long ticketId,
            @PathVariable Long commentId,
            @Valid @RequestBody UpdateCommentRequest request
         ){
        CommentResponse updatedComment = commentService.updateComment(ticketId, commentId, request);
        return ResponseEntity.ok(updatedComment);       

}   
    
    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @PathVariable Long ticketId,
            @PathVariable Long commentId
        ){
        commentService.deleteComment(ticketId, commentId);
        return ResponseEntity.ok().build(); 
        }
}
