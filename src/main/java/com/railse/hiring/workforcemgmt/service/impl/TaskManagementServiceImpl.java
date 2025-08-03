package com.railse.hiring.workforcemgmt.service.impl;

import com.railse.hiring.workforcemgmt.model.Activity;
import com.railse.hiring.workforcemgmt.model.Comment;
import com.railse.hiring.workforcemgmt.model.enums.Priority;
import com.railse.hiring.workforcemgmt.service.ActivityService;
import com.railse.hiring.workforcemgmt.service.TaskManagementService;
import com.railse.hiring.workforcemgmt.common.exception.ResourceNotFoundException;
import com.railse.hiring.workforcemgmt.dto.*;
import com.railse.hiring.workforcemgmt.mapper.ITaskManagementMapper;
import com.railse.hiring.workforcemgmt.model.TaskManagement;
import com.railse.hiring.workforcemgmt.model.enums.Task;
import com.railse.hiring.workforcemgmt.model.enums.TaskStatus;
import com.railse.hiring.workforcemgmt.repository.TaskRepository;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;


@Service
public class TaskManagementServiceImpl implements TaskManagementService {
    private final TaskRepository taskRepository;
    private final ITaskManagementMapper taskMapper;
    private final ActivityService activityService;
    public TaskManagementServiceImpl(TaskRepository taskRepository,
                                     ITaskManagementMapper taskMapper,
                                     ActivityService activityService
                                     ) {
        this.taskRepository = taskRepository;
        this.taskMapper = taskMapper;
        this.activityService = activityService;
    }

    @Override
    public TaskManagementWithActivityAndCommentsDto findTaskById(Long id) {
        TaskManagement task = taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found with id: " + id));
        // Sort comments and activity history in descending order (most recent first)
        task.getComments().sort(Comparator.comparing(Comment::getTimestamp).reversed());
        task.getActivityHistory().sort(Comparator.comparing(Activity::getTimestamp).reversed());
        return taskMapper.modelToDtoWithActivityAndComments(task);
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

            // Initial Logging of activity to the task.
            // Since user is currently not implemented therefore just inserted a demo user.
            List<Activity> activities = new ArrayList<>();
            activities.add(new Activity("Created Task","User", System.currentTimeMillis()));
            newTask.setActivityHistory(activities);
            newTask.setComments(new ArrayList<>());

            // Sort comments and activity history in descending order (most recent first)
            newTask.getComments().sort(Comparator.comparing(Comment::getTimestamp).reversed());
            newTask.getActivityHistory().sort(Comparator.comparing(Activity::getTimestamp).reversed());

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
                activityService.log(item.getTaskId(), new Activity("Task Status Updated to "+ task.getStatus(), "User", System.currentTimeMillis()));
            }
            if (item.getDescription() != null) {
                task.setDescription(item.getDescription());
                activityService.log(item.getTaskId(),  new Activity("Task Description Updated to "+ task.getDescription(), "User", System.currentTimeMillis()));
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
                    activityService.log(taskToUpdate.getId(), new Activity("Task Status Updated to "+ taskToUpdate.getStatus(), "User", System.currentTimeMillis()));
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
                    newTask.setDescription("New task is assigned.");
                    // Initial Logging of activity to the task.
                    // Since user is currently not implemented therefore just inserted a demo user.
                    List<Activity> activities = new ArrayList<>();
                    activities.add(new Activity("Task Created","User", System.currentTimeMillis()));
                    newTask.setActivityHistory(activities);
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

                //When creating new task by default its priority will be LOW
                newTask.setPriority(Priority.LOW);

                // Initial Logging of activity to the task.
                // Since user is currently not implemented therefore just inserted a demo user.
                List<Activity> activities = new ArrayList<>();
                activities.add(new Activity("Task Created","User", System.currentTimeMillis()));
                newTask.setActivityHistory(activities);

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
        List<TaskManagement> filteredTasks = tasks.stream().filter(
                task -> !task.getStatus().equals(TaskStatus.CANCELLED)
        ).toList();
        return taskMapper.modelListToDtoList(filteredTasks);
    }

    @Override
    public TaskManagementDto updateTaskPriority(Long taskId, Priority priority) {
        TaskManagement task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found with id: " + taskId));
        task.setPriority(priority);

        //logging the activity to task
        activityService.log(taskId, new Activity("Updated the task priority to "+priority,"User", System.currentTimeMillis()));


        // Sort comments and activity history in descending order (most recent first)
        task.getComments().sort(Comparator.comparing(Comment::getTimestamp).reversed());
        task.getActivityHistory().sort(Comparator.comparing(Activity::getTimestamp).reversed());
        taskRepository.save(task);
        return taskMapper.modelToDto(task);

    }

    @Override
    public TaskManagementWithActivityAndCommentsDto addComment(Long id, AddCommentRequest comment) {
        TaskManagement task =  taskRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Task not found with id: " + id));
//        if(task.getComments() == null) {
//            task.setComments(new ArrayList<>());
//        }
        Comment c = new Comment();
        c.setAuthor(comment.getAuthor());
        c.setMessage(comment.getComment());
        c.setTimestamp(System.currentTimeMillis());
        task.getComments().add(c);

        // Sort comments and activity history in descending order (most recent first)
        task.getComments().sort(Comparator.comparing(Comment::getTimestamp).reversed());
        task.getActivityHistory().sort(Comparator.comparing(Activity::getTimestamp).reversed());

        return taskMapper.modelToDtoWithActivityAndComments(taskRepository.save(task));

    }
}


