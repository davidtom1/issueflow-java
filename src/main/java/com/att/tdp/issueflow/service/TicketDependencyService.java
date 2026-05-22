package com.att.tdp.issueflow.service;

import com.att.tdp.issueflow.dto.response.TicketDependencyResponse;
import com.att.tdp.issueflow.entity.Ticket;
import com.att.tdp.issueflow.entity.TicketDependency;
import com.att.tdp.issueflow.entity.User;
import com.att.tdp.issueflow.entity.enums.AuditAction;
import com.att.tdp.issueflow.entity.enums.AuditActorType;
import com.att.tdp.issueflow.entity.enums.EntityType;
import com.att.tdp.issueflow.exception.BadRequestException;
import com.att.tdp.issueflow.exception.ConflictException;
import com.att.tdp.issueflow.exception.NotFoundException;
import com.att.tdp.issueflow.repository.TicketDependencyRepository;
import com.att.tdp.issueflow.repository.TicketRepository;
import com.att.tdp.issueflow.repository.UserRepository;
import com.att.tdp.issueflow.security.AuthenticatedUser;

import lombok.RequiredArgsConstructor;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TicketDependencyService {

    private final TicketDependencyRepository ticketDependencyRepository;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;


    @Transactional
    public void addDependency(Long blockedTicketId, Long blockerTicketId, AuthenticatedUser actingUser) {
        if (blockedTicketId.equals(blockerTicketId)) {
            throw new BadRequestException("A ticket cannot depend on itself.");
        }
        Ticket blocked = ticketRepository.findByIdAndDeletedFalse(blockedTicketId)
                .orElseThrow(() -> new NotFoundException("Blocked ticket not found or deleted."));
        Ticket blocker = ticketRepository.findByIdAndDeletedFalse(blockerTicketId)
                .orElseThrow(() -> new NotFoundException("Blocker ticket not found or deleted."));
        if(!blocked.getProject().getId().equals(blocker.getProject().getId())) {
            throw new BadRequestException("Both tickets must belong to the same project.");
        }
        if(ticketDependencyRepository.existsByBlockedTicketIdAndBlockerTicketId(blockedTicketId, blockerTicketId)) {
            throw new ConflictException("This dependency already exists.");
        }
        if(ticketDependencyRepository.existsByBlockedTicketIdAndBlockerTicketId(blockerTicketId, blockedTicketId)) {
            throw new BadRequestException("Adding this dependency would create a circular dependency.");
        }
        User actingUserEntity = userRepository.findById(actingUser.id())
                .orElseThrow(() -> new NotFoundException("Acting user not found."));
        TicketDependency dep = new TicketDependency();
        dep.setBlockedTicket(blocked);
        dep.setBlockerTicket(blocker);
        dep.setCreatedBy(actingUserEntity);
        ticketDependencyRepository.save(dep);

        auditLogService.record(AuditAction.DEPENDENCY_ADD,EntityType.TICKET_DEPENDENCY,dep.getId(),AuditActorType.USER,actingUserEntity.getId(),
        blocked.getProject().getId(),blocked.getId(),null,
        "Ticket "+blockedTicketId+" now depends on ticket "+blockerTicketId);

    }

    @Transactional(readOnly = true)
    public List<TicketDependencyResponse> getDependencies(Long blockedTicketId){
        getVisibleTicket(blockedTicketId); // validate ticket exists and is visible
        return ticketDependencyRepository.findByBlockedTicketId(blockedTicketId)
                .stream()
                .map(dep -> toDependencyResponse(dep.getBlockerTicket()))
                .toList();

    }

    @Transactional
    public void removeDependency(Long blockedTicketId, Long blockerTicketId, long actingUserId) {
        if(!ticketDependencyRepository.existsByBlockedTicketIdAndBlockerTicketId(blockedTicketId, blockerTicketId)) {
            throw new NotFoundException("Dependency not found.");
        }
        Ticket blocked = ticketRepository.findByIdAndDeletedFalse(blockedTicketId)
                .orElseThrow(() -> new NotFoundException("Blocked ticket not found or deleted."));

        TicketDependency dep = ticketDependencyRepository.findByBlockedTicketIdAndBlockerTicketId(blockedTicketId, blockerTicketId)
                .orElseThrow(() -> new NotFoundException("Dependency not found."));
        auditLogService.record(AuditAction.DEPENDENCY_REMOVE,EntityType.TICKET_DEPENDENCY,dep.getId(),AuditActorType.USER,actingUserId,
                blocked.getProject().getId(),blocked.getId(),null,
                "Ticket "+blockedTicketId+" no longer depends on ticket "+blockerTicketId);
        ticketDependencyRepository.deleteByBlockedTicketIdAndBlockerTicketId(blockedTicketId, blockerTicketId);
    }

    private Ticket getVisibleTicket(Long ticketId){
        return ticketRepository.findByIdAndDeletedFalse(ticketId)
                .orElseThrow(() -> new NotFoundException("Ticket not found or deleted."));
    }

    private TicketDependencyResponse toDependencyResponse(Ticket blocker) {
        return new TicketDependencyResponse(
                blocker.getId(),
                blocker.getTitle(),
                blocker.getStatus()
        );
    }
}