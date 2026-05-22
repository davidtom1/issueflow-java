package com.att.tdp.issueflow.service;

import com.att.tdp.issueflow.dto.request.UpdateTicketRequest;
import com.att.tdp.issueflow.entity.Project;
import com.att.tdp.issueflow.entity.ProjectMember;
import com.att.tdp.issueflow.entity.Ticket;
import com.att.tdp.issueflow.entity.TicketDependency;
import com.att.tdp.issueflow.entity.User;
import com.att.tdp.issueflow.entity.enums.TicketPriority;
import com.att.tdp.issueflow.entity.enums.TicketStatus;
import com.att.tdp.issueflow.entity.enums.TicketType;
import com.att.tdp.issueflow.entity.enums.UserRole;
import com.att.tdp.issueflow.exception.ConflictException;
import com.att.tdp.issueflow.repository.ProjectMemberRepository;
import com.att.tdp.issueflow.repository.ProjectRepository;
import com.att.tdp.issueflow.repository.TicketDependencyRepository;
import com.att.tdp.issueflow.repository.TicketRepository;
import com.att.tdp.issueflow.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BlockerRuleTest {

    @Autowired
    private TicketService ticketService;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectMemberRepository projectMemberRepository;

    @Autowired
    private TicketDependencyRepository ticketDependencyRepository;

    @Test
    void openBlockerBlocksDone() {
        Fixture fixture = fixture();
        Ticket blocked = createTicket(fixture.project(), TicketStatus.IN_REVIEW, false);
        Ticket blocker = createTicket(fixture.project(), TicketStatus.IN_PROGRESS, false);
        createDependency(blocked, blocker, fixture.user());

        assertThrows(ConflictException.class, () -> markDone(blocked.getId(), fixture.user().getId()));

        Ticket reloaded = ticketRepository.findById(blocked.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(TicketStatus.IN_REVIEW);
    }

    @Test
    void doneBlockerAllowsDone() {
        Fixture fixture = fixture();
        Ticket blocked = createTicket(fixture.project(), TicketStatus.IN_REVIEW, false);
        Ticket blocker = createTicket(fixture.project(), TicketStatus.DONE, false);
        createDependency(blocked, blocker, fixture.user());

        markDone(blocked.getId(), fixture.user().getId());

        Ticket reloaded = ticketRepository.findById(blocked.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(TicketStatus.DONE);
    }

    @Test
    void softDeletedBlockerAllowsDone() {
        Fixture fixture = fixture();
        Ticket blocked = createTicket(fixture.project(), TicketStatus.IN_REVIEW, false);
        Ticket blocker = createTicket(fixture.project(), TicketStatus.IN_PROGRESS, true);
        createDependency(blocked, blocker, fixture.user());

        markDone(blocked.getId(), fixture.user().getId());

        Ticket reloaded = ticketRepository.findById(blocked.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(TicketStatus.DONE);
    }

    @Test
    void mixedBlockersWithOneStillOpenStillBlockDone() {
        Fixture fixture = fixture();
        Ticket blocked = createTicket(fixture.project(), TicketStatus.IN_REVIEW, false);
        Ticket doneBlocker = createTicket(fixture.project(), TicketStatus.DONE, false);
        Ticket deletedBlocker = createTicket(fixture.project(), TicketStatus.IN_PROGRESS, true);
        Ticket openBlocker = createTicket(fixture.project(), TicketStatus.IN_PROGRESS, false);
        createDependency(blocked, doneBlocker, fixture.user());
        createDependency(blocked, deletedBlocker, fixture.user());
        createDependency(blocked, openBlocker, fixture.user());

        assertThrows(ConflictException.class, () -> markDone(blocked.getId(), fixture.user().getId()));

        Ticket reloaded = ticketRepository.findById(blocked.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(TicketStatus.IN_REVIEW);
    }

    @Test
    void allBlockersResolvedAllowDone() {
        Fixture fixture = fixture();
        Ticket blocked = createTicket(fixture.project(), TicketStatus.IN_REVIEW, false);
        Ticket doneBlocker = createTicket(fixture.project(), TicketStatus.DONE, false);
        Ticket deletedBlocker = createTicket(fixture.project(), TicketStatus.IN_PROGRESS, true);
        createDependency(blocked, doneBlocker, fixture.user());
        createDependency(blocked, deletedBlocker, fixture.user());

        markDone(blocked.getId(), fixture.user().getId());

        Ticket reloaded = ticketRepository.findById(blocked.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(TicketStatus.DONE);
    }

    private void markDone(Long ticketId, Long actorUserId) {
        UpdateTicketRequest request = new UpdateTicketRequest();
        request.setStatus(TicketStatus.DONE);
        ticketService.updateTicket(ticketId, request, actorUserId);
    }

    private Fixture fixture() {
        User user = createUser();
        Project project = createProject(user);
        createProjectMember(project, user);
        return new Fixture(user, project);
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
        project.setDescription("Blocker rule test project");
        project.setOwner(owner);
        return projectRepository.saveAndFlush(project);
    }

    private void createProjectMember(Project project, User user) {
        ProjectMember projectMember = new ProjectMember();
        projectMember.setProject(project);
        projectMember.setUser(user);
        projectMemberRepository.saveAndFlush(projectMember);
    }

    private Ticket createTicket(Project project, TicketStatus status, boolean deleted) {
        Ticket ticket = new Ticket();
        ticket.setProject(project);
        ticket.setTitle("Ticket " + UUID.randomUUID());
        ticket.setDescription("Blocker rule test ticket");
        ticket.setStatus(status);
        ticket.setPriority(TicketPriority.MEDIUM);
        ticket.setType(TicketType.FEATURE);
        ticket.setDeleted(deleted);
        return ticketRepository.saveAndFlush(ticket);
    }

    private void createDependency(Ticket blockedTicket, Ticket blockerTicket, User createdBy) {
        TicketDependency dependency = new TicketDependency();
        dependency.setBlockedTicket(blockedTicket);
        dependency.setBlockerTicket(blockerTicket);
        dependency.setCreatedBy(createdBy);
        ticketDependencyRepository.saveAndFlush(dependency);
    }

    private record Fixture(User user, Project project) {
    }
}
