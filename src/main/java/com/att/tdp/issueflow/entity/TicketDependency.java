package com.att.tdp.issueflow.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
@Entity
@Table(name = "ticket_dependencies", uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_ticket_dependencies_blocked_blocker",
                        columnNames = {"blocked_ticket_id", "blocker_ticket_id"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor

public class TicketDependency {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blocked_ticket_id", nullable = false)
    private Ticket blockedTicket;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blocker_ticket_id", nullable = false)
    private Ticket blockerTicket;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id", nullable = false)
    private User createdBy;

    @Column(nullable = false)
    private Instant createdAt;

    @PrePersist
        void onCreate() {
        this.createdAt = Instant.now();
        }

}
