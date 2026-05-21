package com.att.tdp.issueflow.service;
import com.att.tdp.issueflow.dto.response.CommentResponse;
import com.att.tdp.issueflow.dto.response.MentionedUserResponse;
import com.att.tdp.issueflow.dto.response.PagedResponse;
import com.att.tdp.issueflow.entity.Comment;
import com.att.tdp.issueflow.entity.Mention;
import com.att.tdp.issueflow.entity.User;
import com.att.tdp.issueflow.exception.NotFoundException;
import com.att.tdp.issueflow.repository.MentionRepository;
import com.att.tdp.issueflow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;


import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor

public class MentionService {

    private final MentionRepository mentionRepository;
    private final UserRepository userRepository;
    private static final Pattern MENTION_PATTERN = Pattern.compile("@([A-Za-z0-9_]+)");

    @Transactional
    public void rebuildMentions(Comment comment){
        mentionRepository.deleteByCommentId(comment.getId());
        Set<String> mentionedUsernames = extractMentionedUsernames(comment.getContent());
    for (String username : mentionedUsernames) {
        userRepository.findByUsernameIgnoreCase(username).ifPresent(mentionedUser -> {
            Mention mention = new Mention();
            mention.setComment(comment);
            mention.setTicket(comment.getTicket());
            mention.setProject(comment.getTicket().getProject());
            mention.setMentionedUser(mentionedUser);
            mentionRepository.save(mention);
        });
        }
    }

    @Transactional
    public void clearMentionsForComment(Comment comment){
        mentionRepository.deleteByCommentId(comment.getId());
    }

    @Transactional(readOnly = true)
    public List<MentionedUserResponse> getMentionedUsersForComment(Comment comment) {
        return mentionRepository.findByCommentId(comment.getId())
                .stream()
                .map(Mention::getMentionedUser)
                .map(this::toMentionedUserResponse)
                .toList();
    }
    
    @Transactional(readOnly = true)
    public PagedResponse<CommentResponse> getMentionsForUser(Long userId, int page, int pageSize) {
        userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found with id: " + userId));
        Pageable pageable = PageRequest.of(page - 1, pageSize);   // API is 1-indexed, Spring is 0-indexed
        Page<Mention> mentionsPage =
                mentionRepository.findByMentionedUserIdOrderByCreatedAtDesc(userId, pageable);

        List<CommentResponse> data = mentionsPage.getContent().stream()
                .map(Mention::getComment)
                .map(this::toCommentResponse)
                .toList();

        return new PagedResponse<>(data, mentionsPage.getTotalElements(), page);
    }

    private Set<String> extractMentionedUsernames(String content) { 
        Set<String> usernames = new LinkedHashSet<>();

        if (content == null || content.isBlank()) {
            return usernames;
        }

        Matcher matcher = MENTION_PATTERN.matcher(content);

        while (matcher.find()) {
            String username = matcher.group(1).toLowerCase(Locale.ROOT);
            usernames.add(username);
        }

        return usernames;
    }

    private MentionedUserResponse toMentionedUserResponse(User user){
    return new MentionedUserResponse(user.getId(), user.getUsername(), user.getFullName());
}
    
    private CommentResponse toCommentResponse(Comment comment) {
        List<MentionedUserResponse> mentionedUsers = getMentionedUsersForComment(comment);

        return new CommentResponse(
                comment.getId(),
                comment.getTicket().getId(),
                comment.getAuthor().getId(),
                comment.getAuthor().getUsername(),
                comment.getContent(),
                mentionedUsers,
                comment.getVersion(),
                comment.getCreatedAt(),
                comment.getUpdatedAt()
        );
    }
}
