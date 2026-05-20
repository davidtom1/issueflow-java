package com.att.tdp.issueflow.service;

import com.att.tdp.issueflow.dto.request.LoginRequest;
import com.att.tdp.issueflow.dto.response.AuthResponse;
import com.att.tdp.issueflow.dto.response.UserResponse;
import com.att.tdp.issueflow.security.AuthenticatedUser;
import com.att.tdp.issueflow.entity.InvalidatedToken;
import com.att.tdp.issueflow.entity.User;
import com.att.tdp.issueflow.exception.NotFoundException;
import com.att.tdp.issueflow.exception.UnauthorizedException;
import com.att.tdp.issueflow.security.JwtService;
import com.att.tdp.issueflow.repository.InvalidatedTokenRepository;
import com.att.tdp.issueflow.repository.UserRepository;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;

import java.time.Instant;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;



@Service
@RequiredArgsConstructor


public class AuthService {
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final InvalidatedTokenRepository invalidatedTokenRepository;

    public AuthResponse login(LoginRequest request){
        User user = userRepository.findByUsernameIgnoreCase(request.getUsername())
                .orElseThrow(() -> new UnauthorizedException("Invalid username or password"));
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid username or password");
        }
        String token = jwtService.generateToken(user);
        return new AuthResponse(token, "Bearer", jwtService.getExpirationSeconds());
        
    }

    public UserResponse getCurrentUser(AuthenticatedUser authenticatedUser){
        if (authenticatedUser == null) {
            throw new UnauthorizedException("Authentication required");
        }
        User user = userRepository.findById(authenticatedUser.id())
                .orElseThrow(() -> new NotFoundException("User not found"));
        return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getFullName(), user.getRole(), user.getCreatedAt(), user.getUpdatedAt());

    }



    public void logout(String authorizationHeader){
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new UnauthorizedException("Invalid authorization header");
        }
        try {
            String token = authorizationHeader.substring(7);
            String jti = jwtService.extractTokenId(token);
            Instant timeToExpire = jwtService.extractExpiration(token);
        
            InvalidatedToken invalidToken = new InvalidatedToken();
            invalidToken.setTokenId(jti);
            invalidToken.setExpiresAt(timeToExpire);
            invalidatedTokenRepository.save(invalidToken);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new UnauthorizedException("Invalid token");
        }

    }
}
