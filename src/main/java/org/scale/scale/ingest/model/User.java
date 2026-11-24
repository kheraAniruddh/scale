package org.scale.scale.ingest.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record User(
        @JsonProperty("User_ID") String userId,
        @JsonProperty("Education") String education,
        @JsonProperty("Experience") String experience,
        @JsonProperty("Available_Time") double availableTime) {
}
