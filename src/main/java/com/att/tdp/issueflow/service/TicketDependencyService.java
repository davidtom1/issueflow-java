package com.att.tdp.issueflow.service;

import com.att.tdp.issueflow.dto.response.TicketDependencyResponse;
import com.att.tdp.issueflow.entity.Ticket;
import com.att.tdp.issueflow.entity.TicketDependency;
import com.att.tdp.issueflow.entity.User;
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
        // // TODO audit: action=DEPENDENCY_ADD, entityType=TICKET_DEPENDENCY
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
    public void removeDependency(Long blockedTicketId, Long blockerTicketId){
        if(!ticketDependencyRepository.existsByBlockedTicketIdAndBlockerTicketId(blockedTicketId, blockerTicketId)) {
            throw new NotFoundException("Dependency not found.");
        }
        ticketDependencyRepository.deleteByBlockedTicketIdAndBlockerTicketId(blockedTicketId, blockerTicketId);
        // TODO audit: action=DEPENDENCY_REMOVE, entityType=TICKET_DEPENDENCY   

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