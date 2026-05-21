package com.att.tdp.issueflow.service;

import com.att.tdp.issueflow.entity.ProjectMember;
import com.att.tdp.issueflow.entity.User;
import com.att.tdp.issueflow.entity.enums.TicketStatus;
import com.att.tdp.issueflow.entity.enums.UserRole;
import com.att.tdp.issueflow.repository.ProjectMemberRepository;
import com.att.tdp.issueflow.repository.TicketRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AutoAssignmentService {

    private final ProjectMemberRepository projectMemberRepository;
    private final TicketRepository ticketRepository;

    /**
     * Returns the least-loaded DEVELOPER member of the project, or null if none.
     * Workload = count of non-DONE, non-deleted tickets assigned to the user in this project.
     * Tie-break: oldest createdAt first, then lowest id.
     */
    @Transactional(readOnly = true)
    public User pickAssignee(Long projectId) {
        List<User> developers = projectMemberRepository.findByProjectIdAndUserRole(projectId, UserRole.DEVELOPER).stream()
                .map(ProjectMember::getUser)
                .toList();
        if (developers.isEmpty()) {
            return null; // No developers linked - allowed, just return null    
        }
        List<Object[]> rawCounts = ticketRepository.countOpenTicketsByAssignee(projectId, TicketStatus.DONE);
        Map<Long, Long> countByUserId = new HashMap<>();
        for (Object[] row : rawCounts) {
            Long userId = (Long) row[0];
            Long count = (Long) row[1];
            countByUserId.put(userId, count);
        }
            return developers.stream().min(
                    Comparator.comparingLong((User u) -> countByUserId.getOrDefault(u.getId(), 0L))
                        .thenComparing(User::getCreatedAt)
                        .thenComparing(User::getId))
                .orElse(null);
    }
}