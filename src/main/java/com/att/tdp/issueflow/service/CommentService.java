package com.att.tdp.issueflow.service;
import com.att.tdp.issueflow.dto.request.CreateCommentRequest;
import com.att.tdp.issueflow.dto.request.UpdateCommentRequest;
import com.att.tdp.issueflow.dto.response.CommentResponse;
import com.att.tdp.issueflow.entity.Comment;
import com.att.tdp.issueflow.entity.Ticket;
import com.att.tdp.issueflow.entity.User;
import com.att.tdp.issueflow.entity.enums.AuditAction;
import com.att.tdp.issueflow.entity.enums.AuditActorType;
import com.att.tdp.issueflow.entity.enums.EntityType;
import com.att.tdp.issueflow.exception.NotFoundException;
import com.att.tdp.issueflow.repository.CommentRepository;
import com.att.tdp.issueflow.repository.TicketRepository;
import com.att.tdp.issueflow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final MentionService mentionService;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<CommentResponse> getCommentsForTicket(Long ticketId){
        ticketRepository.findByIdAndProjectDeletedFalseAndDeletedFalse(ticketId)
        .orElseThrow(() -> new NotFoundException("Ticket not found with id: " + ticketId));
        List<Comment> comments = commentRepository.findByTicketIdOrderByCreatedAtAsc(ticketId);
        return comments.stream().map(this::toResponse).toList();
    }

    @Transactional
    public CommentResponse addComment(Long ticketId, CreateCommentRequest request, Long userId){
        Ticket ticket = ticketRepository.findByIdAndProjectDeletedFalseAndDeletedFalse(ticketId)
        .orElseThrow(() -> new NotFoundException("Ticket not found with id: " + ticketId));
        Comment comment = new Comment();
        comment.setContent(request.getContent());
        comment.setTicket(ticket);
        User author = userRepository.findById(userId)
        .orElseThrow(() -> new NotFoundException("User not found with id: " + userId));
        comment.setAuthor(author);
        Comment savedComment = commentRepository.save(comment);
        mentionService.rebuildMentions(savedComment);
        auditLogService.record(AuditAction.CREATE,EntityType.COMMENT,savedComment.getId(),AuditActorType.USER,
            userId,ticket.getProject().getId(),ticket.getId(),null,"New comment created with content: " + savedComment.getContent());
        return toResponse(savedComment);
    }

    @Transactional
    public CommentResponse updateComment(Long ticketId, Long commentId, UpdateCommentRequest request, Long userId){
        Ticket ticket = ticketRepository.findByIdAndProjectDeletedFalseAndDeletedFalse(ticketId)
        .orElseThrow(() -> new NotFoundException("Ticket not found with id: " + ticketId));
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException("User not found with id: " + userId));
        Comment comment = commentRepository.findById(commentId)
        .orElseThrow(() -> new NotFoundException("Comment not found with id: " + commentId));
        validateCommentBelongsToTicket(comment, ticketId);
        comment.setContent(request.getContent());
        Comment updatedComment = commentRepository.save(comment);
        mentionService.rebuildMentions(updatedComment);
        auditLogService.record(AuditAction.UPDATE,EntityType.COMMENT,updatedComment.getId(),AuditActorType.USER,
            user.getId(),ticket.getProject().getId(),ticket.getId(),null,"Comment updated with content: " + updatedComment.getContent());
        
        return toResponse(updatedComment);
    }

    @Transactional
    public void deleteComment(Long ticketId, Long commentId, Long userId){
        ticketRepository.findByIdAndProjectDeletedFalseAndDeletedFalse(ticketId)
            .orElseThrow(() -> new NotFoundException("Ticket not found with id: " + ticketId));
        Comment comment = commentRepository.findById(commentId)
            .orElseThrow(() -> new NotFoundException("Comment not found with id: " + commentId));
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException("User not found with id: " + userId));
        validateCommentBelongsToTicket(comment, ticketId);
        auditLogService.record(AuditAction.DELETE,EntityType.COMMENT,comment.getId(),AuditActorType.USER,
            user.getId(),comment.getTicket().getProject().getId(),comment.getTicket().getId(),null,"Comment deleted with content: " + comment.getContent());
        mentionService.clearMentionsForComment(comment);
        commentRepository.delete(comment);
    }

    private CommentResponse toResponse(Comment comment){
    CommentResponse response = new CommentResponse(
            comment.getId(),
            comment.getTicket().getId(),
            comment.getAuthor().getId(),
            comment.getAuthor().getUsername(),
            comment.getContent(),
            mentionService.getMentionedUsersForComment(comment),
            comment.getVersion(),
            comment.getCreatedAt(),
            comment.getUpdatedAt()
    );
    return response;    
    }

    private void validateCommentBelongsToTicket(Comment comment, Long ticketId){
        if(!comment.getTicket().getId().equals(ticketId)){
            throw new NotFoundException("Comment with id: " + comment.getId() + " does not belong to ticket with id: " + ticketId);
        }
    }
        
    
}
