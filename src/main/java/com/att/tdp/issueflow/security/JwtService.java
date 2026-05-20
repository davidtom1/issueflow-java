package com.att.tdp.issueflow.security;

import com.att.tdp.issueflow.entity.User;
import com.att.tdp.issueflow.entity.enums.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final String secret;
    private final long expirationSeconds;
    private SecretKey signingKey;

    // Values come from app.jwt.* properties, with dev-only defaults for local runs.
    public JwtService(
            @Value("${app.jwt.secret:issueflow-dev-only-secret-key-change-me-before-production-1234567890}") String secret,
            @Value("${app.jwt.expirationSeconds:3600}") long expirationSeconds
    ) {
        this.secret = secret;
        this.expirationSeconds = expirationSeconds;
    }

    @PostConstruct
    void init() {
        // JJWT signs and verifies tokens with a SecretKey, so convert the configured string once at startup.
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(expirationSeconds);

        // The subject is the user id; username and role are separate claims for convenient client context.
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("username", user.getUsername())
                .claim("role", user.getRole().name())
                // jti uniquely identifies this token, which lets logout invalidate one token without storing it.
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
    }

    public Claims parseToken(String token) {
        try {
            // Verifies the signature and expiration before returning the token payload claims.
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            // A signed token is only useful to this app if the expected auth claims are present and valid.
            validateClaims(claims);
            return claims;
        } catch (JwtException | IllegalArgumentException exception) {
            throw new JwtException("Invalid token", exception);
        }
    }

    public Long extractUserId(String token) {
        return Long.valueOf(parseToken(token).getSubject());
    }

    public String extractUsername(String token) {
        return parseToken(token).get("username", String.class);
    }

    public UserRole extractRole(String token) {
        return UserRole.valueOf(parseToken(token).get("role", String.class));
    }

    public String extractTokenId(String token) {
        return parseToken(token).getId();
    }

    public Instant extractExpiration(String token) {
        return parseToken(token).getExpiration().toInstant();
    }

    public long getExpirationSeconds() {
        return expirationSeconds;
    }

    private void validateClaims(Claims claims) {
        // These claims are required by the authentication filter and logout flow.
        if (claims.getSubject() == null
                || claims.getId() == null
                || claims.getExpiration() == null
                || claims.get("username", String.class) == null
                || claims.get("role", String.class) == null) {
            throw new JwtException("Token is missing required claims");
        }

        // Parse these values now so malformed user ids or roles fail during token validation.
        Long.valueOf(claims.getSubject());
        UserRole.valueOf(claims.get("role", String.class));
    }
}
