package com.att.tdp.issueflow.controller;
import com.att.tdp.issueflow.dto.request.CreateTicketRequest;
import com.att.tdp.issueflow.dto.request.UpdateTicketRequest;
import com.att.tdp.issueflow.dto.response.CsvImportResponse;
import com.att.tdp.issueflow.dto.response.TicketResponse;
import com.att.tdp.issueflow.security.AuthenticatedUser;
import com.att.tdp.issueflow.service.TicketCsvService;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.List;
@RestController
@RequestMapping("/tickets")
@RequiredArgsConstructor
public class TicketController {
    private final TicketService ticketService;
    private final TicketCsvService ticketCsvService;


    @GetMapping
    public ResponseEntity<List<TicketResponse>> getTicketsByProject(@RequestParam Long projectId){
        return ResponseEntity.ok(ticketService.getTicketsByProject(projectId));
    }
    @GetMapping("/{ticketId}")
    public ResponseEntity<TicketResponse> getTicketById(@PathVariable Long ticketId){
        return ResponseEntity.ok(ticketService.getTicketById(ticketId));
    }
    @PostMapping
    public ResponseEntity<TicketResponse> createTicket(@Valid @RequestBody CreateTicketRequest request,
         @AuthenticationPrincipal AuthenticatedUser actingUser){
        return ResponseEntity.ok(ticketService.createTicket(request, actingUser.id()));
    }

    @PatchMapping("/{ticketId}")
    public ResponseEntity<TicketResponse> updateTicket(
            @PathVariable Long ticketId,
            @Valid @RequestBody UpdateTicketRequest request,
            @AuthenticationPrincipal AuthenticatedUser actingUser
    ){
        return ResponseEntity.ok(ticketService.updateTicket(ticketId, request, actingUser.id()));
    }

    @DeleteMapping("/{ticketId}")
    public ResponseEntity<Void> deleteTicket(
            @PathVariable Long ticketId,
            @AuthenticationPrincipal AuthenticatedUser actingUser
    ){
        ticketService.softDeleteTicket(ticketId, actingUser);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<String> exportTickets(@RequestParam Long projectId) {
        String csv = ticketCsvService.exportTickets(projectId);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"tickets-" + projectId + ".csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CsvImportResponse> importTickets(
        @RequestPart("file") MultipartFile file,
        @RequestParam Long projectId,
        @AuthenticationPrincipal AuthenticatedUser actingUser
    ) {
        CsvImportResponse response = ticketCsvService.importTickets(projectId, file, actingUser.id());
        return ResponseEntity.ok(response);
    }


    @GetMapping("/deleted")
    public ResponseEntity<List<TicketResponse>> getDeletedTicketsByProject(@RequestParam Long projectId){
        return ResponseEntity.ok(ticketService.getDeletedTicketsByProject(projectId));
    }

    @PostMapping("/{ticketId}/restore")
    public ResponseEntity<Void> restoreTicket(@PathVariable Long ticketId,
        @AuthenticationPrincipal AuthenticatedUser actingUser
    ) {
        ticketService.restoreTicket(ticketId, actingUser.id());
        return ResponseEntity.ok().build();
    }


}
