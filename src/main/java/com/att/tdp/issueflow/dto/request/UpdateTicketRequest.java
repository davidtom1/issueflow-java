package com.att.tdp.issueflow.dto.request;

import com.att.tdp.issueflow.entity.enums.TicketPriority;
import com.att.tdp.issueflow.entity.enums.TicketStatus;
import com.att.tdp.issueflow.entity.enums.TicketType;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UpdateTicketRequest {

    @Size(max = 200)
    private String title;

    private String description;

    private TicketStatus status;

    private TicketPriority priority;

    private TicketType type;

    private Long assigneeId;

    private Instant dueDate;
}
