package com.att.tdp.issueflow.controller;
import com.att.tdp.issueflow.dto.response.AttachmentResponse;
import com.att.tdp.issueflow.security.AuthenticatedUser;
import com.att.tdp.issueflow.service.AttachmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/tickets/{ticketId}/attachments")
@RequiredArgsConstructor

public class AttachmentController {
        private final AttachmentService attachmentService;

        @PostMapping
        public ResponseEntity<AttachmentResponse> uploadAttachment(
            @PathVariable Long ticketId,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ){
                AttachmentResponse response = attachmentService.uploadAttachment(ticketId, file, authenticatedUser);
                return ResponseEntity.ok(response);

    }
    @DeleteMapping("/{attachmentId}")
        public ResponseEntity<Void> deleteAttachment(
                @PathVariable Long ticketId,
                @PathVariable Long attachmentId,
                @AuthenticationPrincipal AuthenticatedUser authenticatedUser
        ){
                attachmentService.deleteAttachment(ticketId, attachmentId, authenticatedUser);
                return ResponseEntity.ok().build();
        }
        
}
