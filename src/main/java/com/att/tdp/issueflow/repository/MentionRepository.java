package com.att.tdp.issueflow.repository;

import com.att.tdp.issueflow.entity.Mention;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

public interface MentionRepository extends JpaRepository<Mention, Long> {
    Page<Mention> findByMentionedUserIdOrderByCreatedAtDesc(Long mentionedUserId, Pageable pageable);
    @Modifying
    @Transactional
    void deleteByCommentId(Long commentId);
}
