package com.coderhino.tools.runtime;

import com.coderhino.services.tasks.TaskRecord;

import java.util.List;
import java.util.Optional;

public interface ToolTaskService {
    TaskRecord create(String description);

    List<TaskRecord> list();

    Optional<TaskRecord> get(String id);

    List<String> getProgressMessages(String id);

    Optional<String> getOutputAwait(String id);

    Optional<TaskRecord> stop(String id);

    boolean cancel(String id);

    Optional<TaskRecord> update(String id, String status);

    Optional<TaskRecord> delete(String id);
}
