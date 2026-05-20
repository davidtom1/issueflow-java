package com.att.tdp.issueflow.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AddProjectMemberRequest {

    @NotNull
    private Long userId;
}
