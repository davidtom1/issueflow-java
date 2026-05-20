package com.att.tdp.issueflow.repository;

import com.att.tdp.issueflow.entity.TicketDependency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface TicketDependencyRepository extends JpaRepository<TicketDependency, Long> {
    List<TicketDependency> findByBlockedTicketId(Long blockedTicketId);
    boolean existsByBlockedTicketIdAndBlockerTicketId(Long blockedTicketId, Long blockerTicketId);

    @Modifying
    @Transactional
    void deleteByBlockedTicketIdAndBlockerTicketId(Long blockedTicketId, Long blockerTicketId);
}
