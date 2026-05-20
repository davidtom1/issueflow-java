package com.att.tdp.issueflow.security;

import com.att.tdp.issueflow.entity.User;
import com.att.tdp.issueflow.repository.InvalidatedTokenRepository;
import com.att.tdp.issueflow.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    // Authorization headers must use the standard Bearer token format: "Bearer <jwt>".
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final InvalidatedTokenRepository invalidatedTokenRepository;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        // This filter runs once per request and looks for a JWT in the Authorization header.
        String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        // Requests without a Bearer token continue unauthenticated; SecurityConfig decides if that is allowed.
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authorizationHeader.substring(BEARER_PREFIX.length());

        try {
            // Parsing verifies the signature, expiration, and required JWT claims.
            Claims claims = jwtService.parseToken(token);
            String tokenId = claims.getId();

            // Logout stores invalidated jti values, so a matching token id is no longer accepted.
            if (tokenId == null || invalidatedTokenRepository.existsByTokenId(tokenId)) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid token");
                return;
            }

            // The JWT subject is the user id; load the database user so role/username reflect current state.
            Long userId = Long.valueOf(claims.getSubject());
            User user = userRepository.findById(userId)
                    .orElse(null);

            // A token for a deleted or missing user should not authenticate the request.
            if (user == null) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid token");
                return;
            }

            // Store a small application principal for controllers that need the authenticated user.
            AuthenticatedUser principal = new AuthenticatedUser(user.getId(), user.getUsername(), user.getRole());

            // Spring Security expects authorities like ROLE_ADMIN or ROLE_DEVELOPER for role checks.
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
            );
            // Save the Authentication for the rest of this request.
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (JwtException | IllegalArgumentException exception) {
            // Invalid, expired, malformed, or otherwise unusable tokens are rejected with 401.
            SecurityContextHolder.clearContext();
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid token");
        }
    }
}
