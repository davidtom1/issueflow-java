package com.att.tdp.issueflow.controller;

import com.att.tdp.issueflow.dto.request.AddDependencyRequest;
import com.att.tdp.issueflow.dto.response.TicketDependencyResponse;
import com.att.tdp.issueflow.security.AuthenticatedUser;
import com.att.tdp.issueflow.service.TicketDependencyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/tickets/{ticketId}/dependencies")
@RequiredArgsConstructor
public class TicketDependencyController {

    private final TicketDependencyService ticketDependencyService;

    @GetMapping
    public ResponseEntity<List<TicketDependencyResponse>> getDependencies(@PathVariable Long ticketId) {
        return ResponseEntity.ok(ticketDependencyService.getDependencies(ticketId));
    }

    @PostMapping
    public ResponseEntity<Void> addDependency(
            @PathVariable Long ticketId,
            @Valid @RequestBody AddDependencyRequest request,
            @AuthenticationPrincipal AuthenticatedUser actingUser
    ) {
        ticketDependencyService.addDependency(ticketId, request.getBlockedBy(), actingUser);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{blockerId}")
    public ResponseEntity<Void> removeDependency(
            @PathVariable Long ticketId,
            @PathVariable Long blockerId,
            @AuthenticationPrincipal AuthenticatedUser actingUser
    ) {
        ticketDependencyService.removeDependency(ticketId, blockerId, actingUser.id());
        return ResponseEntity.ok().build();
    }
}