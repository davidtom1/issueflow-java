package com.att.tdp.issueflow.dto.request;

import com.att.tdp.issueflow.entity.enums.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UpdateUserRequest {

    @Size(max = 50)
    private String username;

    @Email
    private String email;

    @Size(max = 100)
    private String fullName;

    @Size(min = 6)
    private String password;

    private UserRole role;
}
