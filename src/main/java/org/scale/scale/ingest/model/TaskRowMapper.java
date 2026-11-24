package org.scale.scale.ingest.model;

import java.util.List;
import java.util.Map;

import org.scale.scale.ingest.csv.ParsedCsvRow;
import org.springframework.stereotype.Component;

@Component
public class TaskRowMapper implements RowMapper<Task> {

    static final String TASK_ID = "Task_ID";
    static final String TIME_TO_COMPLETE = "Time_To_Complete";
    static final String DEADLINE = "Deadline";
    static final String TASK_STRING = "Task_String";

    @Override
    public List<String> requiredHeaders() {
        return List.of(TASK_ID, TIME_TO_COMPLETE, DEADLINE, TASK_STRING);
    }

    @Override
    public String idColumn() {
        return TASK_ID;
    }

    @Override
    public Task map(ParsedCsvRow row) {
        return new Task(
                RowFields.requireText(row, TASK_ID),
                RowFields.requireNonNegativeNumber(row, TIME_TO_COMPLETE),
                RowFields.requireNonNegativeNumber(row, DEADLINE),
                RowFields.requireText(row, TASK_STRING));
    }

    @Override
    public Map<String, Object> columnValues(Task task) {
        return Map.of(
                TASK_ID, task.taskId(),
                TIME_TO_COMPLETE, task.timeToComplete(),
                DEADLINE, task.deadline(),
                TASK_STRING, task.taskString());
    }
}
