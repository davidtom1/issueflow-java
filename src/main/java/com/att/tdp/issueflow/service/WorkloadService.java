package com.att.tdp.issueflow.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.att.tdp.issueflow.entity.ProjectMember;
import com.att.tdp.issueflow.entity.enums.TicketStatus;
import com.att.tdp.issueflow.repository.ProjectMemberRepository;
import com.att.tdp.issueflow.repository.ProjectRepository;
import com.att.tdp.issueflow.repository.TicketRepository;
import com.att.tdp.issueflow.dto.response.WorkloadEntryResponse;
import com.att.tdp.issueflow.exception.NotFoundException;

import lombok.RequiredArgsConstructor;
@Service
@RequiredArgsConstructor
public class WorkloadService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final TicketRepository ticketRepository;
    
    public List<WorkloadEntryResponse> getWorkload(long projectId) {
        projectRepository.findByIdAndDeletedFalse(projectId)
        .orElseThrow(() -> new NotFoundException("Project not found"));
        List<Object[]> rawCounts = ticketRepository.countOpenTicketsByAssignee(projectId, TicketStatus.DONE);
        Map<Long, Long> countByUserId = new HashMap<>();
        for (Object[] row : rawCounts) {
            Long userId = (Long) row[0];
            Long count = (Long) row[1];
            countByUserId.put(userId, count);
        }
        List<ProjectMember> projectMembers = projectMemberRepository.findByProjectId(projectId);
        List<WorkloadEntryResponse> responses = new ArrayList<>();
        for (ProjectMember pm : projectMembers) {
            Long id = pm.getUser().getId();
            long count = countByUserId.getOrDefault(id, 0L); 
            WorkloadEntryResponse entry = new WorkloadEntryResponse(id, pm.getUser().getUsername(), count);
            responses.add(entry);
        }
        return responses.stream()
        .sorted(Comparator.comparingLong(WorkloadEntryResponse::openTicketCount))
        .toList();

    }

        
        
}
        

