package com.att.tdp.issueflow.service;

import com.att.tdp.issueflow.entity.Ticket;
import com.att.tdp.issueflow.entity.enums.AuditAction;
import com.att.tdp.issueflow.entity.enums.AuditActorType;
import com.att.tdp.issueflow.entity.enums.EntityType;
import com.att.tdp.issueflow.entity.enums.TicketPriority;
import com.att.tdp.issueflow.entity.enums.TicketStatus;
import com.att.tdp.issueflow.repository.TicketRepository;

import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TicketEscalationService {

    private final TicketRepository ticketRepository;
    private final AuditLogService auditLogService;

    @Transactional
    public void escalateOverdueTickets() {
        Instant now = Instant.now();
        Instant cooldownCutoff = now.minus(Duration.ofHours(1));
        for (Ticket ticket : ticketRepository.findOverdueNonDoneNotDeleted(now, TicketStatus.DONE)) {
            if (ticket.getLastAutoEscalatedAt() != null && ticket.getLastAutoEscalatedAt().isAfter(cooldownCutoff)) {
                continue; // Skip if within cooldown period
            }
            TicketPriority oldPriority = ticket.getPriority();
            TicketPriority newPriority = nextPriority(oldPriority);

            if (newPriority != null) {
                ticket.setPriority(newPriority);
                ticket.setOverdue(false);
                ticket.setLastAutoEscalatedAt(now);
                auditLogService.record(
                        AuditAction.AUTO_ESCALATE,
                        EntityType.TICKET,
                        ticket.getId(),
                        AuditActorType.SYSTEM,
                        null,
                        ticket.getProject().getId(),
                        ticket.getId(),
                        "Old priority: " + oldPriority,
                        "New priority: " + newPriority
                );
            } else if (!ticket.isOverdue()) {
                ticket.setOverdue(true);
                ticket.setLastAutoEscalatedAt(now);
                auditLogService.record(
                        AuditAction.AUTO_ESCALATE,
                        EntityType.TICKET,
                        ticket.getId(),
                        AuditActorType.SYSTEM,
                        null,
                        ticket.getProject().getId(),
                        ticket.getId(),
                        "isOverdue=false",
                        "isOverdue=true"
                );
            }
        }
    }

    private TicketPriority nextPriority(TicketPriority priority) {
        return switch (priority) {
            case LOW -> TicketPriority.MEDIUM;
            case MEDIUM -> TicketPriority.HIGH;
            case HIGH -> TicketPriority.CRITICAL;
            case CRITICAL -> null;
        };
    }
}
