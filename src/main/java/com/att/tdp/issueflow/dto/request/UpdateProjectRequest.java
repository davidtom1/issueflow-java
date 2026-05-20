package com.att.tdp.issueflow.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UpdateProjectRequest {

    @Size(max = 100)
    private String name;

    private String description;
}
