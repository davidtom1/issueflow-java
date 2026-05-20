package com.att.tdp.issueflow.repository;

import com.att.tdp.issueflow.entity.Ticket;
import com.att.tdp.issueflow.entity.enums.TicketStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket, Long> {
    // Returns all non-deleted tickets whose due date has passed and are not in the done status
    @Query("""
        SELECT t FROM Ticket t
        WHERE t.dueDate is NOT NULL AND
        t.dueDate < :now AND
        t.deleted = false AND
        t.status != :done"""
            )
        List<Ticket> findOverdue(@Param("now") Instant now, @Param("done") TicketStatus done);

    // Returns each assignee's ID with their count of open (non-done, non-deleted) tickets in the given project
    @Query("""
            SELECT t.assignee.id, COUNT(t) FROM Ticket t
            WHERE t.project.id = :projectId AND
            t.deleted = false AND
            t.status != :done AND
            t.assignee IS NOT NULL
            GROUP BY t.assignee.id
            """)
    List<Object[]> countOpenTicketsByAssignee(@Param("projectId") Long projectId, @Param("done") TicketStatus done);

    List<Ticket> findByProjectIdAndDeletedFalse(Long projectId);
    List<Ticket> findByProjectIdAndDeletedTrue(Long projectId);
    Optional<Ticket> findByIdAndDeletedFalse(Long id);
}

