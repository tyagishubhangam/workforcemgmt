package com.railse.hiring.workforcemgmt.service;


import com.railse.hiring.workforcemgmt.dto.*;
import com.railse.hiring.workforcemgmt.model.enums.Priority;

import java.util.List;


public interface TaskManagementService {

    List<TaskManagementDto> createTasks(TaskCreateRequest request);
    List<TaskManagementDto> updateTasks(UpdateTaskRequest request);
    String assignByReference(AssignByReferenceRequest request);
    List<TaskManagementDto> fetchTasksByDate(TaskFetchByDateRequest request);
    TaskManagementDto findTaskById(Long id);

    // NEW METHOD for Feature 2
    TaskManagementDto updateTaskPriority(Long taskId, Priority priority);
}
