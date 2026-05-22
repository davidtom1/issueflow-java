package com.att.tdp.issueflow.controller;

import com.att.tdp.issueflow.dto.request.AddProjectMemberRequest;
import com.att.tdp.issueflow.dto.response.UserResponse;
import com.att.tdp.issueflow.security.AuthenticatedUser;
import com.att.tdp.issueflow.service.ProjectMemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ProjectMemberController {

    private final ProjectMemberService projectMemberService;

    @PostMapping("/projects/{projectId}/members")
    public ResponseEntity<Void> addMember(
            @PathVariable Long projectId,
            @Valid @RequestBody AddProjectMemberRequest request,
            @AuthenticationPrincipal AuthenticatedUser actingUser
    ) {
        projectMemberService.addMember(projectId, request.getUserId(), actingUser.id());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/projects/{projectId}/members/{userId}")
    public ResponseEntity<Void> removeMember(
        @PathVariable Long projectId,
         @PathVariable Long userId,
         @AuthenticationPrincipal AuthenticatedUser actingUser) {
        projectMemberService.removeMember(projectId, userId, actingUser.id());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/projects/{projectId}/members")
    public ResponseEntity<List<UserResponse>> getProjectMembers(@PathVariable Long projectId) {
        return ResponseEntity.ok(projectMemberService.getProjectMembers(projectId));
    }
}
