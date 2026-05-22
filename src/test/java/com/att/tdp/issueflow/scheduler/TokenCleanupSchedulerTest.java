package com.att.tdp.issueflow.scheduler;

import com.att.tdp.issueflow.entity.InvalidatedToken;
import com.att.tdp.issueflow.repository.InvalidatedTokenRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TokenCleanupSchedulerTest {

    @Autowired
    private TokenCleanupScheduler tokenCleanupScheduler;

    @Autowired
    private InvalidatedTokenRepository invalidatedTokenRepository;

    @Test
    void deleteExpiredTokensRemovesExpiredEntriesAndKeepsValidOnes() {
        InvalidatedToken expired = invalidatedToken("expired-" + UUID.randomUUID(), Instant.now().minus(Duration.ofHours(1)));
        InvalidatedToken valid = invalidatedToken("valid-" + UUID.randomUUID(), Instant.now().plus(Duration.ofHours(1)));

        expired = invalidatedTokenRepository.saveAndFlush(expired);
        valid = invalidatedTokenRepository.saveAndFlush(valid);

        tokenCleanupScheduler.deleteExpiredTokens();

        assertThat(invalidatedTokenRepository.findById(expired.getId())).isEmpty();
        assertThat(invalidatedTokenRepository.findById(valid.getId())).isPresent();
    }

    private InvalidatedToken invalidatedToken(String tokenId, Instant expiresAt) {
        InvalidatedToken invalidatedToken = new InvalidatedToken();
        invalidatedToken.setTokenId(tokenId);
        invalidatedToken.setExpiresAt(expiresAt);
        return invalidatedToken;
    }
}
