package com.att.tdp.issueflow.controller;

import com.att.tdp.issueflow.dto.request.CreateUserRequest;
import com.att.tdp.issueflow.dto.request.UpdateUserRequest;
import com.att.tdp.issueflow.dto.response.UserResponse;
import com.att.tdp.issueflow.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.att.tdp.issueflow.security.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.util.List;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    
    @PostMapping
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request,
        @AuthenticationPrincipal AuthenticatedUser actingUser
    ){
        return ResponseEntity.ok(userService.createUser(request, actingUser));

    }

    @GetMapping
    public ResponseEntity<List<UserResponse>> getAllUsers(){ 
        return ResponseEntity.ok(userService.getAllUsers());     
    }

    @GetMapping("/{userId}")
    public ResponseEntity<UserResponse> getUserById(@PathVariable Long userId){
        return ResponseEntity.ok(userService.getUserById(userId));

    }

    @PostMapping("/update/{userId}")
    public ResponseEntity<UserResponse> updateUser(@PathVariable Long userId,
        @Valid @RequestBody UpdateUserRequest request, 
        @AuthenticationPrincipal AuthenticatedUser actingUser
    ){
        return ResponseEntity.ok(userService.updateUser(userId, request, actingUser.id()));

    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long userId
        , @AuthenticationPrincipal AuthenticatedUser actingUser){
            userService.deleteUser(userId, actingUser.id());
            return ResponseEntity.ok().build();

    }
}
