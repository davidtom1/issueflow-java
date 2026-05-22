package com.att.tdp.issueflow.service;

import com.att.tdp.issueflow.dto.response.AuditLogResponse;
import com.att.tdp.issueflow.entity.AuditLog;
import com.att.tdp.issueflow.entity.User;
import com.att.tdp.issueflow.entity.enums.AuditAction;
import com.att.tdp.issueflow.entity.enums.AuditActorType;
import com.att.tdp.issueflow.entity.enums.EntityType;
import com.att.tdp.issueflow.exception.BadRequestException;
import com.att.tdp.issueflow.exception.NotFoundException;
import com.att.tdp.issueflow.repository.AuditLogRepository;
import com.att.tdp.issueflow.repository.UserRepository;
import lombok.RequiredArgsConstructor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    @Transactional
    public void record(
            AuditAction action,
            EntityType entityType,
            Long entityId,
            AuditActorType actorType,
            Long actorUserId,
            Long projectId,
            Long ticketId,
            String oldValue,
            String newValue
    ) {
        AuditLog auditLog = new AuditLog();
        auditLog.setAction(action);
        auditLog.setEntityType(entityType);
        auditLog.setEntityId(entityId);
        auditLog.setActorType(actorType);
        auditLog.setProjectId(projectId);
        auditLog.setTicketId(ticketId);
        auditLog.setOldValue(oldValue);
        auditLog.setNewValue(newValue);

        if (actorUserId != null) {
            userRepository.findById(actorUserId).ifPresent(auditLog::setActorUser);
        }

        auditLogRepository.save(auditLog);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public List<AuditLogResponse> getAuditLogs(
            EntityType entityType,
            Long entityId,
            AuditAction action,
            String actor
    ) {
        AuditActorType actorType = null;
        Long actorUserId = null;

        if (actor != null && !actor.isBlank()) {
            if (actor.equalsIgnoreCase("SYSTEM")) {
                actorType = AuditActorType.SYSTEM;
            } else {
                actorType = AuditActorType.USER;

                try {
                    actorUserId = Long.parseLong(actor);
                } catch (NumberFormatException exception) {
                    throw new BadRequestException("actor must be a user id or SYSTEM");
                }
            }
        }

        return auditLogRepository.findByFilters(
                        entityType,
                        entityId,
                        action,
                        actorType,
                        actorUserId
                )
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private AuditLogResponse toResponse(AuditLog auditLog) {
        return new AuditLogResponse(
                auditLog.getId(),
                auditLog.getActorType(),
                auditLog.getActorUser() != null ? auditLog.getActorUser().getId() : null,
                auditLog.getAction(),
                auditLog.getEntityType(),
                auditLog.getEntityId(),
                auditLog.getProjectId(),
                auditLog.getTicketId(),
                auditLog.getOldValue(),
                auditLog.getNewValue(),
                auditLog.getCreatedAt()
        );
    }
}