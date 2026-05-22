package com.att.tdp.issueflow.service;
import com.att.tdp.issueflow.dto.response.AttachmentResponse;
import com.att.tdp.issueflow.entity.Attachment;
import com.att.tdp.issueflow.entity.Ticket;
import com.att.tdp.issueflow.entity.User;
import com.att.tdp.issueflow.entity.enums.AuditAction;
import com.att.tdp.issueflow.entity.enums.AuditActorType;
import com.att.tdp.issueflow.entity.enums.EntityType;
import com.att.tdp.issueflow.exception.BadRequestException;
import com.att.tdp.issueflow.exception.NotFoundException;
import com.att.tdp.issueflow.repository.AttachmentRepository;
import com.att.tdp.issueflow.repository.TicketRepository;
import com.att.tdp.issueflow.repository.UserRepository;
import com.att.tdp.issueflow.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Set;
@Service
@RequiredArgsConstructor
public class AttachmentService {
    private final AttachmentRepository attachmentRepository;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private static final long MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
        "image/png",
        "image/jpeg",
        "application/pdf",
        "text/plain"
);

@Transactional
public AttachmentResponse uploadAttachment(
        Long ticketId,
        MultipartFile file,
        AuthenticatedUser authenticatedUser
){
        Ticket ticket = ticketRepository.findByIdAndProjectDeletedFalseAndDeletedFalse(ticketId)
        .orElseThrow(() -> new NotFoundException("Ticket not found with id: " + ticketId));
        User user = userRepository.findById(authenticatedUser.id()).orElseThrow(() -> new NotFoundException("User not found with id: " + authenticatedUser.id()));
        validateFile(file);

        Attachment attachment = new Attachment();
        attachment.setFilename(file.getOriginalFilename());
        attachment.setContentType(file.getContentType());
        attachment.setSizeBytes(file.getSize());
        attachment.setUploadedBy(user);
        attachment.setTicket(ticket);
        try {
            attachment.setData(file.getBytes());
        } catch (IOException e) {
            throw new BadRequestException("Could not read uploaded file");
        }
        Attachment savedAttachment = attachmentRepository.save(attachment);
        auditLogService.record(AuditAction.ATTACHMENT_UPLOAD,EntityType.ATTACHMENT,attachment.getId(),
                            AuditActorType.USER,user.getId(),ticket.getProject().getId(),ticket.getId(),null,
                            "Attachment uploaded with filename: " + savedAttachment.getFilename());
        return toResponse(savedAttachment);
}

@Transactional
public void deleteAttachment(
        Long ticketId,
        Long attachmentId,
        AuthenticatedUser authenticatedUser
){
        Ticket ticket = ticketRepository.findByIdAndProjectDeletedFalseAndDeletedFalse(ticketId)
        .orElseThrow(() -> new NotFoundException("Ticket not found with id: " + ticketId));
        Attachment attachment = attachmentRepository.findById(attachmentId)
        .orElseThrow(() -> new NotFoundException("Attachment not found with id: " + attachmentId));
        if(!attachment.getTicket().getId().equals(ticketId)){
            throw new BadRequestException("Attachment does not belong to the specified ticket");
        }
        User user = userRepository.findById(authenticatedUser.id()).orElseThrow(() -> new NotFoundException("User not found with id: " + authenticatedUser.id()));
        auditLogService.record(AuditAction.ATTACHMENT_DELETE,EntityType.ATTACHMENT,attachment.getId(),
                            AuditActorType.USER,user.getId(),ticket.getProject().getId(),ticket.getId(),null,
                            "Attachment deleted with filename: " + attachment.getFilename());
        attachmentRepository.delete(attachment);
}

    private void validateFile(MultipartFile file){
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File is empty");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new BadRequestException("File size exceeds maximum allowed size of 10MB");
        }
        if (file.getContentType() == null || !ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            throw new BadRequestException("File type not allowed. Allowed types: " + ALLOWED_CONTENT_TYPES);
        }
        if (file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()) {
            throw new BadRequestException("Filename is required");
        }
    }

    private AttachmentResponse toResponse(Attachment attachment){
        return new AttachmentResponse(
                attachment.getId(),
                attachment.getTicket().getId(),
                attachment.getFilename(),
                attachment.getContentType(),
                attachment.getSizeBytes(),
                attachment.getUploadedBy().getId(),
                attachment.getUploadedAt()
        );
    }
        
}
