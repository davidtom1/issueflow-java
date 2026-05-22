package com.att.tdp.issueflow.service;

import com.att.tdp.issueflow.entity.Project;
import com.att.tdp.issueflow.repository.ProjectMemberRepository;
import com.att.tdp.issueflow.repository.ProjectRepository;
import com.att.tdp.issueflow.exception.ConflictException;
import com.att.tdp.issueflow.exception.NotFoundException;
import com.att.tdp.issueflow.repository.UserRepository;

import jakarta.transaction.Transactional;

import com.att.tdp.issueflow.entity.ProjectMember;
import com.att.tdp.issueflow.dto.response.UserResponse;
import com.att.tdp.issueflow.entity.User;
import com.att.tdp.issueflow.entity.enums.AuditAction;
import com.att.tdp.issueflow.entity.enums.AuditActorType;
import com.att.tdp.issueflow.entity.enums.EntityType;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectMemberService {

    private final UserRepository usersRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectRepository projectRepository;
    private final AuditLogService auditLogService;

    @Transactional
    public void addMember(long projectId, long userId, long actingUserId) {
        Project project = projectRepository.findByIdAndDeletedFalse(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found"));
        User user = usersRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        if (projectMemberRepository.existsByProjectIdAndUserId(projectId, userId)) {
            throw new ConflictException("User is already a member of the project");
        }
        ProjectMember member = new ProjectMember();
        member.setProject(project);
        member.setUser(user);
        projectMemberRepository.save(member);
        auditLogService.record(AuditAction.CREATE,EntityType.PROJECT_MEMBER,member.getId(),AuditActorType.USER,
            actingUserId,project.getId(),null,null,"Project member added: " + user.getUsername());

    }

    @Transactional
    public void removeMember(long projectId, long userId, long actingUserId) {
        Project project = projectRepository.findByIdAndDeletedFalse(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found"));
        User user = usersRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        ProjectMember member = projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new NotFoundException("Member not part of the project"));
        auditLogService.record(AuditAction.DELETE,EntityType.PROJECT_MEMBER,member.getId(),AuditActorType.USER,
            actingUserId,project.getId(),null,null,"Project member removed: " + user.getUsername());        
        projectMemberRepository.delete(member);

    }


    public List<UserResponse> getProjectMembers(long projectId) {
        Project project = projectRepository.findByIdAndDeletedFalse(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found"));
        return projectMemberRepository.findByProjectId(projectId).stream()
                .map(ProjectMember::getUser)
                .map(user -> new UserResponse(user.getId(), 
                user.getUsername(), 
                user.getEmail(), 
                user.getFullName(), 
                user.getRole(), 
                user.getCreatedAt(), 
                user.getUpdatedAt()))
                .toList();
    }
    
}
