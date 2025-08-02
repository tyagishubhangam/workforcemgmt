package com.railse.hiring.workforcemgmt.service.impl;

import com.railse.hiring.workforcemgmt.common.exception.ResourceNotFoundException;
import com.railse.hiring.workforcemgmt.mapper.ITaskManagementMapper;
import com.railse.hiring.workforcemgmt.model.Activity;
import com.railse.hiring.workforcemgmt.model.TaskManagement;
import com.railse.hiring.workforcemgmt.repository.TaskRepository;
import com.railse.hiring.workforcemgmt.service.ActivityService;
import org.springframework.stereotype.Service;

@Service
public class ActivityServiceImpl implements ActivityService {
    private final TaskRepository taskRepository;
    private final ITaskManagementMapper taskMapper;

    public ActivityServiceImpl(TaskRepository taskRepository,ITaskManagementMapper taskMapper) {
        this.taskRepository = taskRepository;
        this.taskMapper = taskMapper;
    }
    @Override
    public void log(Long taskId, Activity activity) {
        TaskManagement task = taskRepository.findById(taskId).orElseThrow(()->new ResourceNotFoundException("Task with id: "+taskId+" not found"));
        task.getActivityHistory().add(activity);
        taskRepository.save(task);
    }
}
