package com.att.tdp.issueflow.service;

import com.att.tdp.issueflow.entity.InvalidatedToken;
import com.att.tdp.issueflow.entity.Project;
import com.att.tdp.issueflow.entity.Ticket;
import com.att.tdp.issueflow.entity.User;
import com.att.tdp.issueflow.entity.enums.TicketPriority;
import com.att.tdp.issueflow.entity.enums.TicketStatus;
import com.att.tdp.issueflow.entity.enums.TicketType;
import com.att.tdp.issueflow.entity.enums.UserRole;
import com.att.tdp.issueflow.repository.AuditLogRepository;
import com.att.tdp.issueflow.repository.InvalidatedTokenRepository;
import com.att.tdp.issueflow.repository.ProjectRepository;
import com.att.tdp.issueflow.repository.TicketRepository;
import com.att.tdp.issueflow.repository.UserRepository;
import com.att.tdp.issueflow.scheduler.TokenCleanupScheduler;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class Phase13PersistenceIntegrationTest {

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private InvalidatedTokenRepository invalidatedTokenRepository;

    @Test
    void escalationSkipsDoneNonOverdueDeletedAndAlreadyOverdueCriticalTickets() {
        User owner = userRepository.save(user("phase13_owner"));
        Project project = projectRepository.save(project(owner));

        Ticket done = ticket(project, "done", TicketStatus.DONE, TicketPriority.LOW, Instant.now().minusSeconds(60), false, false);
        Ticket nonOverdue = ticket(project, "future", TicketStatus.TODO, TicketPriority.LOW, Instant.now().plusSeconds(60), false, false);
        Ticket deleted = ticket(project, "deleted", TicketStatus.TODO, TicketPriority.LOW, Instant.now().minusSeconds(60), false, true);
        Ticket criticalAlreadyOverdue = ticket(project, "critical", TicketStatus.TODO, TicketPriority.CRITICAL, Instant.now().minusSeconds(60), true, false);
        ticketRepository.save(done);
        ticketRepository.save(nonOverdue);
        ticketRepository.save(deleted);
        ticketRepository.save(criticalAlreadyOverdue);

        TicketEscalationService service = new TicketEscalationService(
                ticketRepository,
                new AuditLogService(auditLogRepository, userRepository)
        );

        service.escalateOverdueTickets();

        assertThat(ticketRepository.findById(done.getId()).orElseThrow().getPriority()).isEqualTo(TicketPriority.LOW);
        assertThat(ticketRepository.findById(nonOverdue.getId()).orElseThrow().getPriority()).isEqualTo(TicketPriority.LOW);
        assertThat(ticketRepository.findById(deleted.getId()).orElseThrow().getPriority()).isEqualTo(TicketPriority.LOW);
        Ticket critical = ticketRepository.findById(criticalAlreadyOverdue.getId()).orElseThrow();
        assertThat(critical.getPriority()).isEqualTo(TicketPriority.CRITICAL);
        assertThat(critical.isOverdue()).isTrue();
        assertThat(critical.getLastAutoEscalatedAt()).isNull();
        assertThat(auditLogRepository.findAll()).isEmpty();
    }

    @Test
    void tokenCleanupDeletesExpiredInvalidatedTokens() {
        InvalidatedToken expired = token("expired", Instant.now().minusSeconds(60));
        InvalidatedToken active = token("active", Instant.now().plusSeconds(60));
        invalidatedTokenRepository.save(expired);
        invalidatedTokenRepository.save(active);

        new TokenCleanupScheduler(invalidatedTokenRepository).deleteExpiredTokens();

        assertThat(invalidatedTokenRepository.existsByTokenId("expired")).isFalse();
        assertThat(invalidatedTokenRepository.existsByTokenId("active")).isTrue();
    }

    private User user(String username) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setFullName("Phase 13 Owner");
        user.setPasswordHash("password");
        user.setRole(UserRole.ADMIN);
        return user;
    }

    private Project project(User owner) {
        Project project = new Project();
        project.setName("Phase 13 Project");
        project.setDescription("Project for Phase 13 tests");
        project.setOwner(owner);
        return project;
    }

    private Ticket ticket(
            Project project,
            String title,
            TicketStatus status,
            TicketPriority priority,
            Instant dueDate,
            boolean overdue,
            boolean deleted
    ) {
        Ticket ticket = new Ticket();
        ticket.setProject(project);
        ticket.setTitle(title);
        ticket.setDescription(title + " description");
        ticket.setStatus(status);
        ticket.setPriority(priority);
        ticket.setType(TicketType.FEATURE);
        ticket.setDueDate(dueDate);
        ticket.setOverdue(overdue);
        ticket.setDeleted(deleted);
        return ticket;
    }

    private InvalidatedToken token(String tokenId, Instant expiresAt) {
        InvalidatedToken token = new InvalidatedToken();
        token.setTokenId(tokenId);
        token.setExpiresAt(expiresAt);
        return token;
    }
}
