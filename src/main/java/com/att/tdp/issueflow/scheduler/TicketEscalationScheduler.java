package com.att.tdp.issueflow.scheduler;

import com.att.tdp.issueflow.service.TicketEscalationService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TicketEscalationScheduler {

    private final TicketEscalationService ticketEscalationService;

    @Scheduled(fixedDelay = 60000)
    public void escalateOverdueTickets() {
        ticketEscalationService.escalateOverdueTickets();
    }
}
