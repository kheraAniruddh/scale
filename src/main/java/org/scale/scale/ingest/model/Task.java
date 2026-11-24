package org.scale.scale.ingest.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Task(
        @JsonProperty("Task_ID") String taskId,
        @JsonProperty("Time_To_Complete") double timeToComplete,
        @JsonProperty("Deadline") double deadline,
        @JsonProperty("Task_String") String taskString) {
}
