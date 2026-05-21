package com.att.tdp.issueflow.service;
import com.att.tdp.issueflow.dto.request.CreateCommentRequest;
import com.att.tdp.issueflow.dto.request.UpdateCommentRequest;
import com.att.tdp.issueflow.dto.response.CommentResponse;
import com.att.tdp.issueflow.dto.response.MentionedUserResponse;
import com.att.tdp.issueflow.entity.Comment;
import com.att.tdp.issueflow.entity.Ticket;
import com.att.tdp.issueflow.entity.User;
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

    @Transactional(readOnly = true)
    public List<CommentResponse> getCommentsForTicket(Long ticketId){
        ticketRepository.findByIdAndProjectDeletedFalseAndDeletedFalse(ticketId)
        .orElseThrow(() -> new NotFoundException("Ticket not found with id: " + ticketId));
        List<Comment> comments = commentRepository.findByTicketIdOrderByCreatedAtAsc(ticketId);
        return comments.stream().map(this::toResponse).toList();
    }

    @Transactional
    public CommentResponse addComment(Long ticketId, CreateCommentRequest request){
        Ticket ticket = ticketRepository.findByIdAndProjectDeletedFalseAndDeletedFalse(ticketId)
        .orElseThrow(() -> new NotFoundException("Ticket not found with id: " + ticketId));
        Comment comment = new Comment();
        comment.setContent(request.getContent());
        comment.setTicket(ticket);
        User author = userRepository.findById(request.getAuthorId())
        .orElseThrow(() -> new NotFoundException("User not found with id: " + request.getAuthorId()));
        comment.setAuthor(author);
        Comment savedComment = commentRepository.save(comment);
        mentionService.rebuildMentions(savedComment);
        return toResponse(savedComment);
    }

    @Transactional
    public CommentResponse updateComment(Long ticketId, Long commentId, UpdateCommentRequest request){
        ticketRepository.findByIdAndProjectDeletedFalseAndDeletedFalse(ticketId)
        .orElseThrow(() -> new NotFoundException("Ticket not found with id: " + ticketId));
        Comment comment = commentRepository.findById(commentId)
        .orElseThrow(() -> new NotFoundException("Comment not found with id: " + commentId));
        validateCommentBelongsToTicket(comment, ticketId);
        comment.setContent(request.getContent());
        Comment updatedComment = commentRepository.save(comment);
        mentionService.rebuildMentions(updatedComment);
        return toResponse(updatedComment);
    }

    @Transactional
    public void deleteComment(Long ticketId, Long commentId){
        ticketRepository.findByIdAndProjectDeletedFalseAndDeletedFalse(ticketId)
        .orElseThrow(() -> new NotFoundException("Ticket not found with id: " + ticketId));
        Comment comment = commentRepository.findById(commentId)
        .orElseThrow(() -> new NotFoundException("Comment not found with id: " + commentId));
        validateCommentBelongsToTicket(comment, ticketId);
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
