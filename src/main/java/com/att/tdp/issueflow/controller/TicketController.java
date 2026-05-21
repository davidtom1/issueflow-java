package com.att.tdp.issueflow.controller;
import com.att.tdp.issueflow.dto.request.CreateTicketRequest;
import com.att.tdp.issueflow.dto.request.UpdateTicketRequest;
import com.att.tdp.issueflow.dto.response.TicketResponse;
import com.att.tdp.issueflow.security.AuthenticatedUser;
import com.att.tdp.issueflow.service.TicketService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
@RestController
@RequestMapping("/tickets")
@RequiredArgsConstructor
public class TicketController {
    private final TicketService ticketService;

    @GetMapping
    public ResponseEntity<List<TicketResponse>> getTicketsByProject(@RequestParam Long projectId){
        return ResponseEntity.ok(ticketService.getTicketsByProject(projectId));
    }
    @GetMapping("/{ticketId}")
    public ResponseEntity<TicketResponse> getTicketById(@PathVariable Long ticketId){
        return ResponseEntity.ok(ticketService.getTicketById(ticketId));
    }
    @PostMapping
    public ResponseEntity<TicketResponse> createTicket(@Valid @RequestBody CreateTicketRequest request){
        return ResponseEntity.ok(ticketService.createTicket(request));
    }

    @PatchMapping("/{ticketId}")
    public ResponseEntity<TicketResponse> updateTicket(
            @PathVariable Long ticketId,
            @Valid @RequestBody UpdateTicketRequest request
    ){
        return ResponseEntity.ok(ticketService.updateTicket(ticketId, request));

    }
    @DeleteMapping("/{ticketId}")
    public ResponseEntity<Void> deleteTicket(
            @PathVariable Long ticketId,
            @AuthenticationPrincipal AuthenticatedUser actingUser
    ){
        ticketService.softDeleteTicket(ticketId, actingUser);
        return ResponseEntity.ok().build();
    }
}
