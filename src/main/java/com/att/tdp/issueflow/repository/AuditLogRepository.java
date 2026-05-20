package com.att.tdp.issueflow.repository;
import com.att.tdp.issueflow.entity.AuditLog;
import com.att.tdp.issueflow.entity.enums.AuditAction;
import com.att.tdp.issueflow.entity.enums.AuditActorType;
import com.att.tdp.issueflow.entity.enums.EntityType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    // Returns audit log entries matching any combination of entity type, entity ID, action, actor type, and actor user, newest first
    @Query("""
        SELECT a FROM AuditLog a
        WHERE (:entityType IS NULL OR a.entityType = :entityType)
        AND (:entityId IS NULL OR a.entityId = :entityId)
        AND (:action IS NULL OR a.action = :action)
        AND (:actorType IS NULL OR a.actorType = :actorType)
        AND (:actorUserId IS NULL OR a.actorUser.id = :actorUserId)
        ORDER BY a.createdAt DESC
            """)
    List<AuditLog> findByFilters(
            @Param("entityType") EntityType entityType, 
            @Param("entityId") Long entityId,
            @Param("action") AuditAction action,
            @Param("actorType") AuditActorType actorType,
            @Param("actorUserId") Long actorUserId)
             ;


    
}
