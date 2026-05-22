package com.att.tdp.issueflow.service;

import com.att.tdp.issueflow.entity.AuditLog;
import com.att.tdp.issueflow.entity.Project;
import com.att.tdp.issueflow.entity.ProjectMember;
import com.att.tdp.issueflow.entity.Ticket;
import com.att.tdp.issueflow.entity.User;
import com.att.tdp.issueflow.entity.enums.AuditAction;
import com.att.tdp.issueflow.entity.enums.AuditActorType;
import com.att.tdp.issueflow.entity.enums.TicketPriority;
import com.att.tdp.issueflow.entity.enums.TicketStatus;
import com.att.tdp.issueflow.entity.enums.TicketType;
import com.att.tdp.issueflow.entity.enums.UserRole;
import com.att.tdp.issueflow.repository.AuditLogRepository;
import com.att.tdp.issueflow.repository.ProjectMemberRepository;
import com.att.tdp.issueflow.repository.ProjectRepository;
import com.att.tdp.issueflow.repository.TicketRepository;
import com.att.tdp.issueflow.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TicketEscalationServiceTest {

    @Autowired
    private TicketEscalationService ticketEscalationService;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectMemberRepository projectMemberRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    void overdueLowTicketIsPromotedAndWritesSystemAudit() {
        Ticket ticket = createTicket(
                TicketPriority.LOW,
                TicketStatus.TODO,
                Instant.now().minus(Duration.ofDays(1)),
                false,
                null
        );

        ticketEscalationService.escalateOverdueTickets();

        Ticket reloaded = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertThat(reloaded.getPriority()).isEqualTo(TicketPriority.MEDIUM);

        List<AuditLog> auditLogs = auditLogRepository.findAll();
        assertThat(auditLogs)
                .anySatisfy(auditLog -> {
                    assertThat(auditLog.getAction()).isEqualTo(AuditAction.AUTO_ESCALATE);
                    assertThat(auditLog.getActorType()).isEqualTo(AuditActorType.SYSTEM);
                    assertThat(auditLog.getActorUser()).isNull();
                    assertThat(auditLog.getTicketId()).isEqualTo(ticket.getId());
                });
    }

    @Test
    void overdueCriticalTicketHitsCeilingAndFlipsOverdue() {
        Ticket ticket = createTicket(
                TicketPriority.CRITICAL,
                TicketStatus.IN_PROGRESS,
                Instant.now().minus(Duration.ofDays(1)),
                false,
                null
        );

        ticketEscalationService.escalateOverdueTickets();

        Ticket reloaded = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertThat(reloaded.getPriority()).isEqualTo(TicketPriority.CRITICAL);
        assertThat(reloaded.isOverdue()).isTrue();
    }

    @Test
    void cooldownControlsWhetherOverdueTicketEscalates() {
        Ticket recentlyEscalated = createTicket(
                TicketPriority.LOW,
                TicketStatus.TODO,
                Instant.now().minus(Duration.ofDays(1)),
                false,
                Instant.now().minus(Duration.ofMinutes(5))
        );
        Ticket longAgoEscalated = createTicket(
                TicketPriority.LOW,
                TicketStatus.TODO,
                Instant.now().minus(Duration.ofDays(1)),
                false,
                Instant.now().minus(Duration.ofHours(2))
        );

        ticketEscalationService.escalateOverdueTickets();

        Ticket recentReloaded = ticketRepository.findById(recentlyEscalated.getId()).orElseThrow();
        Ticket longAgoReloaded = ticketRepository.findById(longAgoEscalated.getId()).orElseThrow();
        assertThat(recentReloaded.getPriority()).isEqualTo(TicketPriority.LOW);
        assertThat(longAgoReloaded.getPriority()).isEqualTo(TicketPriority.MEDIUM);
    }

    @Test
    void notOverdueTicketIsUntouched() {
        Ticket ticket = createTicket(
                TicketPriority.LOW,
                TicketStatus.TODO,
                Instant.now().plus(Duration.ofDays(1)),
                false,
                null
        );

        ticketEscalationService.escalateOverdueTickets();

        Ticket reloaded = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertThat(reloaded.getPriority()).isEqualTo(TicketPriority.LOW);
    }

    @Test
    void doneTicketIsUntouched() {
        Ticket ticket = createTicket(
                TicketPriority.LOW,
                TicketStatus.DONE,
                Instant.now().minus(Duration.ofDays(1)),
                false,
                null
        );

        ticketEscalationService.escalateOverdueTickets();

        Ticket reloaded = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertThat(reloaded.getPriority()).isEqualTo(TicketPriority.LOW);
    }

    private Ticket createTicket(
            TicketPriority priority,
            TicketStatus status,
            Instant dueDate,
            boolean overdue,
            Instant lastAutoEscalatedAt
    ) {
        User owner = createUser();
        Project project = createProject(owner);
        createProjectMember(project, owner);

        Ticket ticket = new Ticket();
        ticket.setProject(project);
        ticket.setTitle("Ticket " + UUID.randomUUID());
        ticket.setDescription("Escalation test ticket");
        ticket.setStatus(status);
        ticket.setPriority(priority);
        ticket.setType(TicketType.FEATURE);
        ticket.setDueDate(dueDate);
        ticket.setOverdue(overdue);
        ticket.setLastAutoEscalatedAt(lastAutoEscalatedAt);
        return ticketRepository.saveAndFlush(ticket);
    }

    private User createUser() {
        String suffix = UUID.randomUUID().toString();
        User user = new User();
        user.setUsername("developer-" + suffix);
        user.setEmail("developer-" + suffix + "@example.com");
        user.setFullName("Developer " + suffix);
        user.setPasswordHash("hash");
        user.setRole(UserRole.DEVELOPER);
        return userRepository.saveAndFlush(user);
    }

    private Project createProject(User owner) {
        Project project = new Project();
        project.setName("Project " + UUID.randomUUID());
        project.setDescription("Escalation test project");
        project.setOwner(owner);
        return projectRepository.saveAndFlush(project);
    }

    private void createProjectMember(Project project, User user) {
        ProjectMember projectMember = new ProjectMember();
        projectMember.setProject(project);
        projectMember.setUser(user);
        projectMemberRepository.saveAndFlush(projectMember);
    }
}
