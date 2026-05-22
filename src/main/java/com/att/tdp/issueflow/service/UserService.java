package com.att.tdp.issueflow.service;

import com.att.tdp.issueflow.dto.request.CreateUserRequest;
import com.att.tdp.issueflow.dto.request.UpdateUserRequest;
import com.att.tdp.issueflow.dto.response.UserResponse;
import com.att.tdp.issueflow.entity.User;
import com.att.tdp.issueflow.entity.enums.AuditAction;
import com.att.tdp.issueflow.entity.enums.AuditActorType;
import com.att.tdp.issueflow.entity.enums.EntityType;
import com.att.tdp.issueflow.entity.enums.UserRole;
import com.att.tdp.issueflow.exception.ConflictException;
import com.att.tdp.issueflow.exception.ForbiddenException;
import com.att.tdp.issueflow.exception.NotFoundException;
import com.att.tdp.issueflow.repository.UserRepository;
import com.att.tdp.issueflow.security.AuthenticatedUser;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    @Transactional
    public UserResponse createUser(CreateUserRequest request, AuthenticatedUser actingUser) {
        if(userRepository.existsByUsernameIgnoreCase(request.getUsername())) {
            throw new ConflictException("Username already exists");
        }
        if(userRepository.existsByEmailIgnoreCase(request.getEmail())) {
            throw new ConflictException("Email already exists");
        }
        boolean callerIsAdmin = actingUser != null
            && userRepository.findById(actingUser.id())
                 .map(u -> u.getRole() == UserRole.ADMIN)
                 .orElse(false);
        boolean isFirstUser = userRepository.count() == 0;
        if (request.getRole() == UserRole.ADMIN && !callerIsAdmin && !isFirstUser) {
            throw new ForbiddenException("Only an authenticated admin can create an ADMIN user");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setRole(request.getRole());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName());

        User savedUser = userRepository.save(user);
        Long actorId = (actingUser != null) ? actingUser.id() : savedUser.getId();
        auditLogService.record(AuditAction.CREATE,EntityType.USER,savedUser.getId(),AuditActorType.USER,actorId,
                null,null,null,
                "User created: "+savedUser.getUsername());


        return toResponse(savedUser);
    }
    
    @Transactional(readOnly = true)
    public UserResponse getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found"));
        return toResponse(user);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {
        List<User> users = userRepository.findAll();
        return users.stream().map(this::toResponse).toList();
    }

    @Transactional
    public UserResponse updateUser(Long id, UpdateUserRequest request,Long actingUserId) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found"));
        if(request.getEmail() != null && !request.getEmail().equalsIgnoreCase(user.getEmail())) {
            if(userRepository.existsByEmailIgnoreCase(request.getEmail())) {
                throw new ConflictException("Email already exists");
            }
            user.setEmail(request.getEmail());
        }
        if(request.getFullName() != null) {
            user.setFullName(request.getFullName());
        }
        if(request.getPassword() != null) {
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }
        if(request.getRole() != null && request.getRole() != user.getRole()) {
            boolean actorIsAdmin = userRepository.findById(actingUserId)
                    .map(u -> u.getRole() == UserRole.ADMIN)
                    .orElse(false);
            if (!actorIsAdmin) {
                throw new ForbiddenException("Only an admin can change a user's role");
            }
            user.setRole(request.getRole());
        }
        auditLogService.record(AuditAction.UPDATE,EntityType.USER,user.getId(),AuditActorType.USER,actingUserId,
                null,null,null,
                "User "+actingUserId+" updated user: "+user.getUsername());

        return toResponse(user);
    }

    @Transactional
    public void deleteUser(Long id,Long actingUserId) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found"));
        try {
            auditLogService.record(AuditAction.DELETE,EntityType.USER,user.getId(),AuditActorType.USER,actingUserId,
                null,null,null,
                "User "+actingUserId+" deleted: "+user.getUsername());
            userRepository.delete(user);
            userRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("User is referenced and cannot be deleted");
        }
    }

    
    private UserResponse toResponse(User user) {
        UserResponse response = new UserResponse(user.getId(),
        user.getUsername(), 
        user.getEmail(), 
        user.getFullName(), 
        user.getRole(), 
        user.getCreatedAt(), 
        user.getUpdatedAt());
        return response;
    }

}
