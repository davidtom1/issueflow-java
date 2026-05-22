package com.att.tdp.issueflow.service;

import com.att.tdp.issueflow.dto.request.CreateTicketRequest;
import com.att.tdp.issueflow.dto.request.UpdateTicketRequest;
import com.att.tdp.issueflow.dto.response.TicketResponse;
import com.att.tdp.issueflow.entity.Ticket;
import com.att.tdp.issueflow.entity.TicketDependency;
import com.att.tdp.issueflow.exception.NotFoundException;
import com.att.tdp.issueflow.repository.ProjectRepository;
import com.att.tdp.issueflow.repository.TicketDependencyRepository;
import com.att.tdp.issueflow.repository.TicketRepository;
import com.att.tdp.issueflow.repository.UserRepository;
import com.att.tdp.issueflow.security.AuthenticatedUser;
import com.att.tdp.issueflow.entity.enums.AuditAction;
import com.att.tdp.issueflow.entity.enums.AuditActorType;
import com.att.tdp.issueflow.entity.enums.EntityType;
import com.att.tdp.issueflow.entity.enums.TicketStatus;
import com.att.tdp.issueflow.entity.Project;
import com.att.tdp.issueflow.exception.BadRequestException;
import com.att.tdp.issueflow.exception.ConflictException;
import com.att.tdp.issueflow.entity.User;
import com.att.tdp.issueflow.repository.ProjectMemberRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TicketService {

    private final TicketRepository ticketRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final AutoAssignmentService autoAssignmentService;
    private final ProjectMemberRepository projectMemberRepository;
    private final TicketDependencyRepository ticketDependencyRepository;
    private final AuditLogService auditLogService;
    private static final Map<TicketStatus, Set<TicketStatus>> ALLOWED_TRANSITIONS = Map.of(
    TicketStatus.TODO,        Set.of(TicketStatus.IN_PROGRESS),
    TicketStatus.IN_PROGRESS, Set.of(TicketStatus.IN_REVIEW),
    TicketStatus.IN_REVIEW,   Set.of(TicketStatus.DONE),
    TicketStatus.DONE,        Set.of()   // terminal: no legal transitions out
    );

    @Transactional
    public TicketResponse createTicket(CreateTicketRequest request, Long actorUserId) {
        Project project = projectRepository.findByIdAndDeletedFalse(request.getProjectId())
                .orElseThrow(() -> new NotFoundException("Project not found with id: " + request.getProjectId()));
        User assignee;
        boolean autoAssigned = false;
        if (request.getAssigneeId() != null) {
            assignee = userRepository.findById(request.getAssigneeId())
                    .orElseThrow(() -> new NotFoundException("User not found with id: " + request.getAssigneeId()));
            validateAssigneeBelongsToProject(request.getProjectId(), assignee.getId());
        } else {
            assignee = autoAssignmentService.pickAssignee(request.getProjectId());
            autoAssigned = assignee != null;
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
        auditLogService.record(AuditAction.CREATE,EntityType.TICKET,savedTicket.getId(),
                            AuditActorType.USER,actorUserId,project.getId(),savedTicket.getId(),null,
                            "Ticket created with title: " + ticket.getTitle());
        if (autoAssigned) {
            auditLogService.record(AuditAction.AUTO_ASSIGN,EntityType.TICKET,savedTicket.getId(),
                            AuditActorType.SYSTEM,null,project.getId(),savedTicket.getId(),null,
                            "Ticket auto-assigned to user: " + assignee.getUsername());
        } else if (request.getAssigneeId() != null) {
             auditLogService.record(AuditAction.ASSIGN,EntityType.TICKET,savedTicket.getId(),
                            AuditActorType.USER,actorUserId,project.getId(),savedTicket.getId(),null,
                            "Ticket assigned to user: " + assignee.getUsername());
        }
        
        return toResponse(savedTicket);
    }

    @Transactional
    public TicketResponse updateTicket(Long id, UpdateTicketRequest request, Long actorUserId) {
        Ticket ticket = ticketRepository.findByIdAndProjectDeletedFalseAndDeletedFalse(id)
                .orElseThrow(() -> new NotFoundException("Ticket not found with id: " + id));
        Long oldAssigneeId = ticket.getAssignee() != null ? ticket.getAssignee().getId() : null;
        TicketStatus oldStatus = ticket.getStatus();
        boolean nonStatusFieldChanged = false;
        if(ticket.getStatus() == TicketStatus.DONE) {
            throw new ConflictException("Cannot update a ticket that is DONE");
        }
        if(request.getStatus() != null && request.getStatus() != ticket.getStatus()) {
            if (!isValidTransition(ticket.getStatus(), request.getStatus())) {
                throw new BadRequestException("Invalid status transition from " + ticket.getStatus() + " to " + request.getStatus());
            }
            if (request.getStatus() == TicketStatus.DONE) {
                for (TicketDependency dep : ticketDependencyRepository.findByBlockedTicketId(id)) {
                    Ticket blocker = dep.getBlockerTicket();
                    if (blocker.getStatus() != TicketStatus.DONE && !blocker.isDeleted()) {
                        throw new ConflictException("Cannot mark ticket as DONE while blocked by unresolved tickets.");
                    }
                }
            }
            ticket.setStatus(request.getStatus());
        }
        if(request.getPriority() != null && request.getPriority() != ticket.getPriority()) {
            ticket.setPriority(request.getPriority());
            nonStatusFieldChanged = true;
            // Clear escalation state on manual priority change
            ticket.setOverdue(false);
            ticket.setLastAutoEscalatedAt(null);
        }
                if (request.getTitle() != null) {
            ticket.setTitle(request.getTitle());
            nonStatusFieldChanged = true;
        }
        if (request.getDescription() != null) {
            ticket.setDescription(request.getDescription());
            nonStatusFieldChanged = true;
        }
        if (request.getType() != null) {
            ticket.setType(request.getType());
            nonStatusFieldChanged = true;
        }
        if (request.getDueDate() != null) {
            ticket.setDueDate(request.getDueDate());
            nonStatusFieldChanged = true;
        }
        User assignee;
        if (request.getAssigneeId() != null) {
            validateAssigneeBelongsToProject(ticket.getProject().getId(), request.getAssigneeId());
            assignee = userRepository.findById(request.getAssigneeId())
                    .orElseThrow(() -> new NotFoundException("User not found with id: " + request.getAssigneeId()));    
            ticket.setAssignee(assignee);
        }

        Ticket savedTicket = ticketRepository.save(ticket);
        Long newAssigneeId = savedTicket.getAssignee() != null ? savedTicket.getAssignee().getId() : null;
        if (request.getAssigneeId() != null && !Objects.equals(oldAssigneeId, newAssigneeId)) {
            auditLogService.record(AuditAction.ASSIGN,EntityType.TICKET,savedTicket.getId(),AuditActorType.USER,actorUserId,
                    savedTicket.getProject().getId(),savedTicket.getId(),oldAssigneeId == null ? null : "Old assignee id: " + oldAssigneeId,
                    "New assignee id: " + newAssigneeId);
            }
        if (request.getStatus() != null && oldStatus != savedTicket.getStatus()) {
            auditLogService.record(AuditAction.STATUS_CHANGE,EntityType.TICKET,savedTicket.getId(),
            AuditActorType.USER,actorUserId,savedTicket.getProject().getId(),savedTicket.getId(),
            "Old status: " + oldStatus,"New status: " + savedTicket.getStatus());
            }
        if (nonStatusFieldChanged) {
            auditLogService.record(AuditAction.UPDATE,EntityType.TICKET,ticket.getId(),
                            AuditActorType.USER,actorUserId,ticket.getProject().getId(),ticket.getId(),null,
                            "Ticket updated with title: " + ticket.getTitle());
        }
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
        auditLogService.record(AuditAction.DELETE,EntityType.TICKET,ticket.getId(),
                            AuditActorType.USER,actingUserObj.getId(),ticket.getProject().getId(),ticket.getId(),null,
                            "Ticket deleted with title: " + ticket.getTitle());
        ticketRepository.save(ticket);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public List<TicketResponse> getDeletedTicketsByProject(Long projectId){
        return ticketRepository.findByProjectIdAndProjectDeletedFalseAndDeletedTrue(projectId).stream()
                .map(this::toResponse)
                .toList();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void restoreTicket(Long ticketId, Long actingUser) {
        Ticket ticket = ticketRepository.findById(ticketId)
        .orElseThrow(() -> new NotFoundException("Ticket not found with id: " + ticketId));
        if (!ticket.isDeleted()) {
            throw new BadRequestException("Ticket is not deleted");
        }

        if (ticket.getProject().isDeleted()) {
            throw new BadRequestException("Cannot restore ticket while project is deleted");
        }
        ticket.setDeleted(false);
        ticket.setDeletedAt(null);
        ticket.setDeletedBy(null);
        auditLogService.record(AuditAction.RESTORE,EntityType.TICKET,ticket.getId(),
                            AuditActorType.USER,actingUser,ticket.getProject().getId(),ticket.getId(),null,
                            "Ticket restored with title: " + ticket.getTitle());
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
