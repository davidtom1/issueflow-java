package com.att.tdp.issueflow.service;
import com.att.tdp.issueflow.dto.request.CreateProjectRequest;
import com.att.tdp.issueflow.dto.request.UpdateProjectRequest;
import com.att.tdp.issueflow.dto.response.ProjectResponse;
import com.att.tdp.issueflow.entity.Project;
import com.att.tdp.issueflow.entity.ProjectMember;
import com.att.tdp.issueflow.repository.ProjectMemberRepository;
import com.att.tdp.issueflow.repository.ProjectRepository;
import com.att.tdp.issueflow.repository.UserRepository;
import com.att.tdp.issueflow.security.AuthenticatedUser;
import com.att.tdp.issueflow.exception.NotFoundException;
import lombok.RequiredArgsConstructor;

import com.att.tdp.issueflow.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;      

@RequiredArgsConstructor
@Service
public class ProjectService {
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<ProjectResponse> getAllProjects() {
        return projectRepository.findByDeletedFalse().stream()
                .map(this::toResponse)  
                .toList();
    }

    @Transactional(readOnly = true)
    public ProjectResponse getProjectById(Long id) {
        Project project = projectRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new NotFoundException("Project not found with id: " + id));
        return toResponse(project);
    }

    @Transactional
    public ProjectResponse createProject(CreateProjectRequest request){
        User owner = userRepository.findById(request.getOwnerId())
                .orElseThrow(() -> new NotFoundException("Owner not found with id: " + request.getOwnerId()));
        Project project = new Project();
        project.setName(request.getName());
        project.setDescription(request.getDescription());
        project.setOwner(owner);
        Project savedProject = projectRepository.save(project);
        ProjectMember ownerMembership = new ProjectMember();
        ownerMembership.setProject(savedProject);
        ownerMembership.setUser(owner);
        projectMemberRepository.save(ownerMembership);
        return toResponse(savedProject);

    }     

    @Transactional
    public void updateProject(Long id, UpdateProjectRequest request) {
        Project existingProject = projectRepository.findByIdAndDeletedFalse(id).orElseThrow(() -> new NotFoundException("Project not found with id: " + id));
        if (request.getName() != null) {
            existingProject.setName(request.getName());
        }
        if (request.getDescription() != null) {
            existingProject.setDescription(request.getDescription());
        }
        projectRepository.save(existingProject);
    }

    @Transactional
    public void softDeleteProject(Long id, AuthenticatedUser actingUser) {
        Project existingProject = projectRepository.findByIdAndDeletedFalse(id).orElseThrow(() -> new NotFoundException("Project not found with id: " + id));
        existingProject.setDeleted(true);
        existingProject.setDeletedAt(java.time.Instant.now());
        User actingUserEntity = userRepository.findById(actingUser.id())
                .orElseThrow(() -> new NotFoundException("User not found"));
        existingProject.setDeletedBy(actingUserEntity); 
        projectRepository.save(existingProject);
    }

    private ProjectResponse toResponse(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getOwner().getId(),
                project.getOwner().getUsername(),
                project.getVersion(),
                project.getCreatedAt(),
                project.getUpdatedAt()
        );
    }

}
