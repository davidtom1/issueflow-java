package com.att.tdp.issueflow.controller;

import com.att.tdp.issueflow.dto.request.CreateProjectRequest;
import com.att.tdp.issueflow.dto.request.UpdateProjectRequest;
import com.att.tdp.issueflow.dto.response.ProjectResponse;
import com.att.tdp.issueflow.dto.response.WorkloadEntryResponse;
import com.att.tdp.issueflow.security.AuthenticatedUser;
import com.att.tdp.issueflow.service.ProjectService;
import com.att.tdp.issueflow.service.WorkloadService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
@RequestMapping("/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final WorkloadService workloadService;

    @GetMapping
    public ResponseEntity<List<ProjectResponse>> getAllProjects() {
        return ResponseEntity.ok(projectService.getAllProjects());
    }

    @GetMapping("/{projectId}")
    public ResponseEntity<ProjectResponse> getProjectById(@PathVariable Long projectId) {
        return ResponseEntity.ok(projectService.getProjectById(projectId));
    }

    @PostMapping
    public ResponseEntity<ProjectResponse> createProject(
            @Valid @RequestBody CreateProjectRequest request,
            @AuthenticationPrincipal AuthenticatedUser actingUser
    ) {
        return ResponseEntity.ok(projectService.createProject(request, actingUser.id()));
    }

    @PatchMapping("/{projectId}")
    public ResponseEntity<Void> updateProject(
            @PathVariable Long projectId,
            @Valid @RequestBody UpdateProjectRequest request,
            @AuthenticationPrincipal AuthenticatedUser actingUser
    ) {
        projectService.updateProject(projectId, request, actingUser);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/deleted")
    public ResponseEntity<List<ProjectResponse>> getDeletedProjects() {
        return ResponseEntity.ok(projectService.getDeletedProjects());
    }

    @DeleteMapping("/{projectId}")
    public ResponseEntity<Void> softDeleteProject(
            @PathVariable Long projectId,
            @AuthenticationPrincipal AuthenticatedUser actingUser
    ) {
        projectService.softDeleteProject(projectId, actingUser);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{projectId}/workload")
    public ResponseEntity<List<WorkloadEntryResponse>> getWorkload(@PathVariable Long projectId) {
        return ResponseEntity.ok(workloadService.getWorkload(projectId));
    }
    
    @PostMapping("/{projectId}/restore")
    public ResponseEntity<Void> restoreProject(@PathVariable Long projectId,@AuthenticationPrincipal AuthenticatedUser actingUser){
        projectService.restoreProject(projectId, actingUser);
        return ResponseEntity.ok().build();
    }
}
