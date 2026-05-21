package com.att.tdp.issueflow.service;

import com.att.tdp.issueflow.dto.request.CreateTicketRequest;
import com.att.tdp.issueflow.dto.request.UpdateTicketRequest;
import com.att.tdp.issueflow.dto.response.TicketResponse;
import com.att.tdp.issueflow.entity.Ticket;
import com.att.tdp.issueflow.exception.NotFoundException;
import com.att.tdp.issueflow.repository.ProjectRepository;
import com.att.tdp.issueflow.repository.TicketRepository;
import com.att.tdp.issueflow.repository.UserRepository;
import com.att.tdp.issueflow.security.AuthenticatedUser;
import com.att.tdp.issueflow.entity.enums.TicketStatus;
import com.att.tdp.issueflow.entity.Project;
import com.att.tdp.issueflow.exception.BadRequestException;
import com.att.tdp.issueflow.entity.User;
import com.att.tdp.issueflow.repository.ProjectMemberRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TicketService {

    private final TicketRepository ticketRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final AutoAssignmentService autoAssignmentService;
    private final ProjectMemberRepository projectMemberRepository;
    private static final Map<TicketStatus, Set<TicketStatus>> ALLOWED_TRANSITIONS = Map.of(
    TicketStatus.TODO,        Set.of(TicketStatus.IN_PROGRESS),
    TicketStatus.IN_PROGRESS, Set.of(TicketStatus.IN_REVIEW),
    TicketStatus.IN_REVIEW,   Set.of(TicketStatus.DONE),
    TicketStatus.DONE,        Set.of()   // terminal: no legal transitions out
    );

    @Transactional
    public TicketResponse createTicket(CreateTicketRequest request) {
        Project project = projectRepository.findByIdAndDeletedFalse(request.getProjectId())
                .orElseThrow(() -> new NotFoundException("Project not found with id: " + request.getProjectId()));
        User assignee;
        if (request.getAssigneeId() != null) {
            assignee = userRepository.findById(request.getAssigneeId())
                    .orElseThrow(() -> new NotFoundException("User not found with id: " + request.getAssigneeId()));
            validateAssigneeBelongsToProject(request.getProjectId(), assignee.getId());
        } else {
            assignee = autoAssignmentService.pickAssignee(request.getProjectId());
        }
        Ticket ticket = new Ticket();
        ticket.setProject(project);
        ticket.setTitle(request.getTitle());
        ticket.setDescription(request.getDescription());
        ticket.setStatus(request.getStatus());
        ticket.setPriority(request.getPriority());
        ticket.setType(request.getType());
        ticket.setAssignee(assignee);
        ticket.setDueDate(request.getDueDate());
        Ticket savedTicket = ticketRepository.save(ticket);
        return toResponse(savedTicket);
    }

    @Transactional
    public TicketResponse updateTicket(Long id, UpdateTicketRequest request) {
        Ticket ticket = ticketRepository.findByIdAndProjectDeletedFalseAndDeletedFalse(id)
                .orElseThrow(() -> new NotFoundException("Ticket not found with id: " + id));
        if(ticket.getStatus() == TicketStatus.DONE) {
            throw new BadRequestException("Cannot update a ticket that is DONE");
        }
        if(request.getStatus() != null && request.getStatus() != ticket.getStatus()) {
            if (!isValidTransition(ticket.getStatus(), request.getStatus())) {
                throw new BadRequestException("Invalid status transition from " + ticket.getStatus() + " to " + request.getStatus());
            }
            // TODO Phase 8: if requested == DONE, reject if any blocker is unresolved.
            ticket.setStatus(request.getStatus());
        }
        if(request.getPriority() != null && request.getPriority() != ticket.getPriority()) {
            ticket.setPriority(request.getPriority());
            // Clear escalation state on manual priority change
            ticket.setOverdue(false);
            ticket.setLastAutoEscalatedAt(null);
        }
                if (request.getTitle() != null) {
            ticket.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            ticket.setDescription(request.getDescription());
        }
        if (request.getType() != null) {
            ticket.setType(request.getType());
        }
        if (request.getDueDate() != null) {
            ticket.setDueDate(request.getDueDate());
        }
        User assignee;
        if (request.getAssigneeId() != null) {
            validateAssigneeBelongsToProject(ticket.getProject().getId(), request.getAssigneeId());
            assignee = userRepository.findById(request.getAssigneeId())
                    .orElseThrow(() -> new NotFoundException("User not found with id: " + request.getAssigneeId()));    
            ticket.setAssignee(assignee);
        }
        ticketRepository.save(ticket);
        return toResponse(ticket);
    }

    @Transactional(readOnly = true)
    public List<TicketResponse> getTicketsByProject(Long projectId) {
        return ticketRepository.findByProjectIdAndProjectDeletedFalseAndDeletedFalse(projectId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TicketResponse getTicketById(Long id) {
        Ticket ticket = ticketRepository.findByIdAndProjectDeletedFalseAndDeletedFalse(id)
        .orElseThrow(() -> new NotFoundException("Ticket not found with id: " + id));
        return toResponse(ticket);
    }

    @Transactional
    public void softDeleteTicket(Long ticketId, AuthenticatedUser actingUser){
        Ticket ticket = ticketRepository.findByIdAndProjectDeletedFalseAndDeletedFalse(ticketId)
                .orElseThrow(() -> new NotFoundException("Ticket not found with id: " + ticketId));
        User actingUserObj = userRepository.findById(actingUser.id())
                .orElseThrow(() -> new NotFoundException("User not found with id: " + actingUser.id()));
        ticket.setDeleted(true);
        ticket.setDeletedAt(java.time.Instant.now());
        ticket.setDeletedBy(actingUserObj);
        ticketRepository.save(ticket);
    }

    private TicketResponse toResponse(Ticket ticket) {
        TicketResponse response = new TicketResponse(
                ticket.getId(),
                ticket.getProject().getId(),
                ticket.getTitle(),
                ticket.getDescription(),
                ticket.getStatus(),
                ticket.getPriority(),
                ticket.getType(),
                ticket.getAssignee() != null ? ticket.getAssignee().getId() : null,
                ticket.getAssignee() != null ? ticket.getAssignee().getUsername() : null,
                ticket.getDueDate(),
                ticket.isOverdue(),
                ticket.getLastAutoEscalatedAt(),
                ticket.getVersion(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt()
        );
        return response;
    }
    
    private boolean isValidTransition(TicketStatus current, TicketStatus requested) {
        if (current == requested) {
            return true; // no-op, not really changing
        }
        return ALLOWED_TRANSITIONS.get(current).contains(requested);
    }

    private void validateAssigneeBelongsToProject(Long projectId, Long assigneeId){
        if (!projectMemberRepository.existsByProjectIdAndUserId(projectId, assigneeId)) {
            throw new BadRequestException("User is not a member of the project");
        }
    }





}