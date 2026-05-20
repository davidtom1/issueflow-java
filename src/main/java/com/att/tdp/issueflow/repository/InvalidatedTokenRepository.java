package com.att.tdp.issueflow.repository;

import com.att.tdp.issueflow.entity.InvalidatedToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

public interface InvalidatedTokenRepository extends JpaRepository<InvalidatedToken, Long> {
    boolean existsByTokenId(String tokenId);

    @Modifying
    @Transactional
    void deleteByExpiresAtBefore(Instant expiresAt);
}
