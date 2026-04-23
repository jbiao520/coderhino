package com.coderhino.web.controller;

import com.coderhino.services.ServiceRegistry;
import com.coderhino.web.dto.TaskCompletionDto;
import com.coderhino.web.dto.TaskCompletionListDto;
import com.coderhino.web.notifications.CompletionNotificationStore;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {
    private final ServiceRegistry serviceRegistry;
    private final CompletionNotificationStore completionNotificationStore;

    public TaskController(ServiceRegistry serviceRegistry, CompletionNotificationStore completionNotificationStore) {
        this.serviceRegistry = serviceRegistry;
        this.completionNotificationStore = completionNotificationStore;
    }

    @GetMapping(value = "/completions", produces = MediaType.APPLICATION_JSON_VALUE)
    public TaskCompletionListDto listCompletions(@RequestParam(value = "since", required = false) Long since) {
        var sinceInstant = since == null ? null : Instant.ofEpochMilli(Math.max(0L, since));
        var taskCompletions = serviceRegistry.tasks().listCompletedAfter(sinceInstant).stream()
            .map(TaskCompletionDto::from);
        var aiRunCompletions = completionNotificationStore.listCompletedAfter(sinceInstant).stream()
            .map(TaskCompletionDto::from);
        var completions = java.util.stream.Stream.concat(taskCompletions, aiRunCompletions)
            .sorted(java.util.Comparator.comparing(TaskCompletionDto::completedAt, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
            .toList();
        return new TaskCompletionListDto(completions);
    }
}
