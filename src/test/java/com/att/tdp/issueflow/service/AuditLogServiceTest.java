package com.att.tdp.issueflow.service;

import com.att.tdp.issueflow.entity.AuditLog;
import com.att.tdp.issueflow.entity.User;
import com.att.tdp.issueflow.entity.enums.AuditAction;
import com.att.tdp.issueflow.entity.enums.AuditActorType;
import com.att.tdp.issueflow.entity.enums.EntityType;
import com.att.tdp.issueflow.exception.BadRequestException;
import com.att.tdp.issueflow.repository.AuditLogRepository;
import com.att.tdp.issueflow.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AuditLogService auditLogService;

    @Test
    void recordWithUserActorLoadsUserAndSavesAuditLog() {
        User actor = new User();
        actor.setId(5L);
        when(userRepository.findById(5L)).thenReturn(Optional.of(actor));

        auditLogService.record(
                AuditAction.UPDATE,
                EntityType.TICKET,
                10L,
                AuditActorType.USER,
                5L,
                20L,
                10L,
                "old",
                "new"
        );

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog saved = captor.getValue();

        assertThat(saved.getAction()).isEqualTo(AuditAction.UPDATE);
        assertThat(saved.getEntityType()).isEqualTo(EntityType.TICKET);
        assertThat(saved.getEntityId()).isEqualTo(10L);
        assertThat(saved.getActorType()).isEqualTo(AuditActorType.USER);
        assertThat(saved.getActorUser()).isSameAs(actor);
        assertThat(saved.getProjectId()).isEqualTo(20L);
        assertThat(saved.getTicketId()).isEqualTo(10L);
        assertThat(saved.getOldValue()).isEqualTo("old");
        assertThat(saved.getNewValue()).isEqualTo("new");
    }

    @Test
    void recordWithSystemActorDoesNotLoadUserAndSavesAuditLog() {
        auditLogService.record(
                AuditAction.AUTO_ASSIGN,
                EntityType.TICKET,
                10L,
                AuditActorType.SYSTEM,
                null,
                20L,
                10L,
                null,
                "assigned"
        );

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog saved = captor.getValue();

        assertThat(saved.getActorType()).isEqualTo(AuditActorType.SYSTEM);
        assertThat(saved.getActorUser()).isNull();
        assertThat(saved.getAction()).isEqualTo(AuditAction.AUTO_ASSIGN);
        verify(userRepository, never()).findById(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void getAuditLogsWithSystemActorFiltersBySystemActorType() {
        when(auditLogRepository.findByFilters(null, null, null, AuditActorType.SYSTEM, null))
                .thenReturn(List.of());

        auditLogService.getAuditLogs(null, null, null, "SYSTEM");

        verify(auditLogRepository).findByFilters(null, null, null, AuditActorType.SYSTEM, null);
    }

    @Test
    void getAuditLogsWithUserIdActorFiltersByUserActorAndId() {
        when(auditLogRepository.findByFilters(EntityType.TICKET, 1L, AuditAction.CREATE, AuditActorType.USER, 5L))
                .thenReturn(List.of());

        auditLogService.getAuditLogs(EntityType.TICKET, 1L, AuditAction.CREATE, "5");

        verify(auditLogRepository).findByFilters(EntityType.TICKET, 1L, AuditAction.CREATE, AuditActorType.USER, 5L);
    }

    @Test
    void getAuditLogsWithInvalidActorThrowsBadRequest() {
        assertThatThrownBy(() -> auditLogService.getAuditLogs(null, null, null, "someone"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("actor must be a user id or SYSTEM");
    }
}
