package org.scale.scale.classifier;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param taskCategories      labels a task may receive
 * @param userSpecializations labels a user may receive
 * @param maxRetries          retries after the first attempt before a row is dropped
 * @param initialBackoff      wait before the first retry; doubles on each further retry
 */
@ConfigurationProperties(prefix = "classifier")
public record ClassifierProperties(List<String> taskCategories, List<String> userSpecializations,
                                   int maxRetries, Duration initialBackoff) {

    public ClassifierProperties {
        if (taskCategories == null || taskCategories.isEmpty()
                || userSpecializations == null || userSpecializations.isEmpty()) {
            throw new IllegalArgumentException("classifier.task-categories and classifier.user-specializations are required");
        }
        if (maxRetries < 0 || initialBackoff == null || initialBackoff.isNegative()) {
            throw new IllegalArgumentException("classifier.max-retries must be >= 0 and classifier.initial-backoff >= 0");
        }
        taskCategories = List.copyOf(taskCategories);
        userSpecializations = List.copyOf(userSpecializations);
    }
}
