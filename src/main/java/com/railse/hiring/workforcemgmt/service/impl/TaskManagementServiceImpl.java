package com.railse.hiring.workforcemgmt.service.impl;

import com.railse.hiring.workforcemgmt.model.enums.Priority;
import com.railse.hiring.workforcemgmt.service.TaskManagementService;
import com.railse.hiring.workforcemgmt.common.exception.ResourceNotFoundException;
import com.railse.hiring.workforcemgmt.dto.*;
import com.railse.hiring.workforcemgmt.mapper.ITaskManagementMapper;
import com.railse.hiring.workforcemgmt.model.TaskManagement;
import com.railse.hiring.workforcemgmt.model.enums.Task;
import com.railse.hiring.workforcemgmt.model.enums.TaskStatus;
import com.railse.hiring.workforcemgmt.repository.TaskRepository;
import com.railse.hiring.workforcemgmt.service.TaskManagementService;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;


@Service
public class TaskManagementServiceImpl implements TaskManagementService {
    private final TaskRepository taskRepository;
    private final ITaskManagementMapper taskMapper;
    public TaskManagementServiceImpl(TaskRepository taskRepository,
                                     ITaskManagementMapper taskMapper) {
        this.taskRepository = taskRepository;
        this.taskMapper = taskMapper;
    }

    @Override
    public TaskManagementDto findTaskById(Long id) {
        TaskManagement task = taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found with id: " + id));
        return taskMapper.modelToDto(task);
    }
    @Override
    public List<TaskManagementDto> createTasks(TaskCreateRequest
                                                       createRequest) {
        List<TaskManagement> createdTasks = new ArrayList<>();
        for (TaskCreateRequest.RequestItem item : createRequest.getRequests()) {
            TaskManagement newTask = new TaskManagement();
            newTask.setReferenceId(item.getReferenceId());
            newTask.setReferenceType(item.getReferenceType());
            newTask.setTask(item.getTask());
            newTask.setAssigneeId(item.getAssigneeId());
            newTask.setPriority(item.getPriority());
            newTask.setTaskDeadlineTime(item.getTaskDeadlineTime());
            newTask.setStatus(TaskStatus.ASSIGNED);
            newTask.setDescription("New task created.");
            createdTasks.add(taskRepository.save(newTask));
        }
        return taskMapper.modelListToDtoList(createdTasks);
    }


    @Override
    public List<TaskManagementDto> updateTasks(UpdateTaskRequest
                                                       updateRequest) {
        List<TaskManagement> updatedTasks = new ArrayList<>();
        for (UpdateTaskRequest.RequestItem item:updateRequest.getRequests()) {
            TaskManagement task = taskRepository.findById(item.getTaskId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Task not found with id: " + item.getTaskId()));
            if (item.getTaskStatus() != null) {
                task.setStatus(item.getTaskStatus());
            }
            if (item.getDescription() != null) {
                task.setDescription(item.getDescription());
            }
            updatedTasks.add(taskRepository.save(task));
        }
        return taskMapper.modelListToDtoList(updatedTasks);
    }
    @Override
    public String assignByReference(AssignByReferenceRequest request) {
        List<Task> applicableTasks =
                Task.getTasksByReferenceType(request.getReferenceType());
        List<TaskManagement> existingTasks =
                taskRepository.findByReferenceIdAndReferenceType(request.getReferenceId(),
                        request.getReferenceType());
        for (Task taskType : applicableTasks) {
            List<TaskManagement> tasksOfType = existingTasks.stream()
                    .filter(t -> t.getTask() == taskType && t.getStatus()
                            != TaskStatus.COMPLETED).collect(Collectors.toList());




            //FIX: BUG #1
            // Fixed the logic so that when a task is reassigned, the old task for the previous employee is marked as CANCELLED.
            if (!tasksOfType.isEmpty()) {
                for (TaskManagement taskToUpdate : tasksOfType) {
                    // Updating the task to cancelled for previous assignee
                    taskToUpdate.setStatus(TaskStatus.CANCELLED);
                    taskToUpdate.setDescription("This task is cancelled.");
                    taskRepository.save(taskToUpdate);

                    // Creating new task with new assigneeId
                    TaskManagement newTask = new TaskManagement();
                    newTask.setReferenceId(taskToUpdate.getReferenceId());
                    newTask.setReferenceType(taskToUpdate.getReferenceType());
                    newTask.setTask(taskToUpdate.getTask());
                    newTask.setAssigneeId(request.getAssigneeId());
                    newTask.setPriority(taskToUpdate.getPriority());
                    newTask.setTaskDeadlineTime(taskToUpdate.getTaskDeadlineTime());
                    newTask.setStatus(TaskStatus.ASSIGNED);
                    newTask.setDescription("New task assigned.");
                    taskRepository.save(newTask);


                }
            } else {
            // Create a new task if none exist
                TaskManagement newTask = new TaskManagement();
                newTask.setReferenceId(request.getReferenceId());
                newTask.setReferenceType(request.getReferenceType());
                newTask.setTask(taskType);
                newTask.setAssigneeId(request.getAssigneeId());
                newTask.setStatus(TaskStatus.ASSIGNED);
                taskRepository.save(newTask);
            }
        }
        return "Tasks assigned successfully for reference " +
                request.getReferenceId();
    }
    @Override
    public List<TaskManagementDto> fetchTasksByDate(TaskFetchByDateRequest
                                                            request) {
        List<TaskManagement> tasks;
        if(request.getAssigneeIds() == null || request.getAssigneeIds().isEmpty()) {
            tasks = taskRepository.findAll();
        }else{
            tasks =
                    taskRepository.findByAssigneeIdIn(request.getAssigneeIds());
        }

        // FIX: BUG #2 is fixed. It now filters out CANCELLED tasks means it donot displays cancelled tasks.
        // It now checks against startDate and endDate.

        List<TaskManagement> filteredTasks = tasks.stream().filter(
                task -> !task.getStatus().equals(TaskStatus.CANCELLED) && !task.getStatus().equals(TaskStatus.COMPLETED))
                .filter(task ->
                        // Either deadline is within the range
                        // OR deadline is before startDate (still active, overdue/pending)
                        task.getTaskDeadlineTime() < request.getStartDate() || task.getTaskDeadlineTime() <= request.getEndDate()
                )
                .collect(Collectors.toList());
        return taskMapper.modelListToDtoList(filteredTasks);
    }


    // For getting tasks of specific priority
    @Override
    public List<TaskManagementDto> fetchTasksByPriority(Priority priority) {
        List<TaskManagement> tasks = taskRepository.findByPriority(priority);
        return taskMapper.modelListToDtoList(tasks);
    }

    @Override
    public TaskManagementDto updateTaskPriority(Long taskId, Priority priority) {
        TaskManagement task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found with id: " + taskId));
        task.setPriority(priority);
        taskRepository.save(task);
        return taskMapper.modelToDto(task);







    }
}


