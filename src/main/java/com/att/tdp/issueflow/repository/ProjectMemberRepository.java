package com.att.tdp.issueflow.repository;

import com.att.tdp.issueflow.entity.ProjectMember;
import com.att.tdp.issueflow.entity.enums.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, Long> {
    List<ProjectMember> findByProjectId(Long projectId);
    List<ProjectMember> findByProjectIdAndUserRole(Long projectId, UserRole userRole);
    boolean existsByProjectIdAndUserId(Long projectId, Long userId);
    Optional<ProjectMember> findByProjectIdAndUserId(Long projectId, Long userId);
}
