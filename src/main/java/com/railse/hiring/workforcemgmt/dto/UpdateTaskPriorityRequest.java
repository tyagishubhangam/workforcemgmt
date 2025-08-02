package com.railse.hiring.workforcemgmt.dto;

import com.railse.hiring.workforcemgmt.model.enums.Priority;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateTaskPriorityRequest {
    @NotNull(message = "Priority should not be null")
    private Priority priority;
}
