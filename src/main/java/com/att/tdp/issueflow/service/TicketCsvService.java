package com.att.tdp.issueflow.service;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.att.tdp.issueflow.dto.response.CsvImportErrorResponse;
import com.att.tdp.issueflow.dto.response.CsvImportResponse;
import com.att.tdp.issueflow.entity.Project;
import com.att.tdp.issueflow.entity.Ticket;
import com.att.tdp.issueflow.entity.User;
import com.att.tdp.issueflow.entity.enums.AuditAction;
import com.att.tdp.issueflow.entity.enums.AuditActorType;
import com.att.tdp.issueflow.entity.enums.EntityType;
import com.att.tdp.issueflow.entity.enums.TicketPriority;
import com.att.tdp.issueflow.entity.enums.TicketStatus;
import com.att.tdp.issueflow.entity.enums.TicketType;
import com.att.tdp.issueflow.exception.BadRequestException;
import com.att.tdp.issueflow.exception.NotFoundException;
import com.att.tdp.issueflow.repository.ProjectMemberRepository;
import com.att.tdp.issueflow.repository.ProjectRepository;
import com.att.tdp.issueflow.repository.TicketRepository;
import com.att.tdp.issueflow.repository.UserRepository;

import jakarta.transaction.Transactional;

import java.io.Reader;
import java.io.StringWriter;

import org.apache.commons.csv.CSVPrinter;
import lombok.RequiredArgsConstructor;
@RequiredArgsConstructor
@Service
public class TicketCsvService {

    private final TicketRepository ticketRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final AuditLogService auditLogService;
    private final AutoAssignmentService autoAssignmentService;

    public String exportTickets(Long projectId) {
        projectRepository.findByIdAndDeletedFalse(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found with id: " + projectId));
        List<Ticket> tickets = ticketRepository.findByProjectIdAndProjectDeletedFalseAndDeletedFalse(projectId);        StringWriter writer = new StringWriter();
        try (
                CSVPrinter printer = new CSVPrinter(
                        writer,
                        CSVFormat.DEFAULT.builder()
                                .setHeader("id", "title", "description", "status", "priority", "type", "assigneeId")
                                .build()
                )
        ) {
            for (Ticket ticket : tickets) {
                printer.printRecord(
                        ticket.getId(),
                        ticket.getTitle(),
                        ticket.getDescription(),
                        ticket.getStatus(),
                        ticket.getPriority(),
                        ticket.getType(),
                        ticket.getAssignee() != null ? ticket.getAssignee().getId() : ""
                );
            }
        } catch (IOException e) {
            throw new BadRequestException("Could not write CSV file");
        }
        
        return writer.toString();
    }

    @Transactional
    public CsvImportResponse importTickets(Long projectId,MultipartFile file, Long actingUserId){
        validateImportFile(file);
        Project project = projectRepository.findByIdAndDeletedFalse(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found with id: " + projectId));
        int createdCount = 0;
        List<CsvImportErrorResponse> errors = new ArrayList<>();
        try (
            Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
            CSVParser parser = CSVFormat.DEFAULT.builder()
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .setTrim(true)
                    .build()
                    .parse(reader)
        ){
        int rowNumber = 0;
        for (CSVRecord record : parser) {
            rowNumber++;

            try {
                Ticket ticket = buildTicketFromCsvRecord(record, project);
                ticketRepository.save(ticket);
                createdCount++;
            } catch (IllegalArgumentException e) {
                errors.add(new CsvImportErrorResponse(rowNumber, e.getMessage()));
            }
        }

        } catch (IOException e) {
            throw new BadRequestException("Could not read CSV file");
        }
        auditLogService.record(AuditAction.CSV_IMPORT,EntityType.PROJECT,project.getId(),AuditActorType.USER,actingUserId,
        project.getId(),null,null,
        "Imported tickets from CSV file. Created: " + createdCount + ", failed: " + errors.size());
        return new CsvImportResponse(
                createdCount,
                errors.size(),
                errors
        );
    }


 
    private void validateImportFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("CSV file is empty");
        }

        String filename = file.getOriginalFilename();

        if (filename == null || !filename.endsWith(".csv")) {
            throw new BadRequestException("File must be a CSV file");
        }
    }

    private Ticket buildTicketFromCsvRecord(CSVRecord record, Project project) {
        String title = required(record, "title");
        String description = optional(record, "description");

        TicketStatus status = parseEnum(record, "status", TicketStatus.class);
        TicketPriority priority = parseEnum(record, "priority", TicketPriority.class);
        TicketType type = parseEnum(record, "type", TicketType.class);

        User assignee = parseAssignee(record, project);

        Ticket ticket = new Ticket();
        ticket.setProject(project);
        ticket.setTitle(title);
        ticket.setDescription(description);
        ticket.setStatus(status);
        ticket.setPriority(priority);
        ticket.setType(type);
        ticket.setAssignee(assignee);

        return ticket;
    }

    private String required(CSVRecord record, String column) {
        String value = optional(record, column);
        if (value == null) {
            throw new IllegalArgumentException("Missing required field: " + column);
        }
        return value;
    }

    private String optional(CSVRecord record, String column) {
        if (!record.isMapped(column)) {
            return null;
        }
        String value = record.get(column);
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private <E extends Enum<E>> E parseEnum(
        CSVRecord record,
        String column,
        Class<E> enumClass
    ) {
        String value = required(record, column);

        try {
            return Enum.valueOf(enumClass, value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid " + column + ": " + value);
        }
    }

    private User parseAssignee(CSVRecord record, Project project) {
        String value = optional(record, "assigneeId");
        if (value == null) {
            return autoAssignmentService.pickAssignee(project.getId());
        }
        Long assigneeId;
        try {
            assigneeId = Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid assigneeId: " + value);
        }
        User assignee = userRepository.findById(assigneeId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + assigneeId));
        if (!projectMemberRepository.existsByProjectIdAndUserId(project.getId(), assigneeId)) {
            throw new IllegalArgumentException("User is not a member of the project");
        }
        return assignee;
    }
}
