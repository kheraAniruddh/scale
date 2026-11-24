package org.scale.scale.classifier;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.scale.scale.ingest.model.Task;
import org.scale.scale.ingest.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Asks the LLM for a task category or user specialization and accepts only a label
 * from the configured list. Transient failures and unknown labels are retried with
 * exponential backoff; when retries run out the result is empty and the row is dropped.
 * {@link LlmRejectedException} is not retried and propagates.
 */
@Component
public class CategoryClassifier {

    private static final Logger log = LoggerFactory.getLogger(CategoryClassifier.class);

    private final LlmClient llmClient;
    private final ClassifierProperties properties;
    private final Sleeper sleeper;

    public CategoryClassifier(LlmClient llmClient, ClassifierProperties properties, Sleeper sleeper) {
        this.llmClient = llmClient;
        this.properties = properties;
        this.sleeper = sleeper;
    }

    public Optional<String> classifyTask(Task task) {
        List<String> allowed = properties.taskCategories();
        String prompt = "Classify this task into exactly one of these categories: " + String.join(", ", allowed) + ".\n"
                + "Reply with the category name only, exactly as written in the list.\n\n"
                + "Task: " + task.taskString();
        return classify(prompt, allowed, "Task_ID " + task.taskId());
    }

    public Optional<String> classifyUser(User user) {
        List<String> allowed = properties.userSpecializations();
        String prompt = "Classify this user's specialization into exactly one of these: " + String.join(", ", allowed) + ".\n"
                + "Reply with the specialization name only, exactly as written in the list.\n\n"
                + "Education: " + user.education() + "\n"
                + "Experience: " + user.experience();
        return classify(prompt, allowed, "User_ID " + user.userId());
    }

    private Optional<String> classify(String prompt, List<String> allowed, String rowRef) {
        int attempts = properties.maxRetries() + 1;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            if (attempt > 1) {
                sleeper.sleep(backoffBefore(attempt));
            }
            try {
                String reply = llmClient.complete(prompt);
                Optional<String> label = matchAllowed(reply, allowed);
                if (label.isPresent()) {
                    return label;
                }
                log.warn("{}: attempt {}/{} returned a label not in the allowed list: '{}'", rowRef, attempt, attempts, reply);
            } catch (TransientLlmException e) {
                log.warn("{}: attempt {}/{} failed: {}", rowRef, attempt, attempts, e.getMessage());
            }
        }
        log.warn("{}: dropped after {} attempts", rowRef, attempts);
        return Optional.empty();
    }

    /** Wait before attempt n (n >= 2): initial, 2x initial, 4x initial, ... */
    private Duration backoffBefore(int attempt) {
        return properties.initialBackoff().multipliedBy(1L << (attempt - 2));
    }

    /** Case-insensitive match after trimming whitespace, quotes and a trailing period. */
    static Optional<String> matchAllowed(String reply, List<String> allowed) {
        if (reply == null) {
            return Optional.empty();
        }
        String cleaned = reply.strip();
        if (cleaned.endsWith(".")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        cleaned = cleaned.replaceAll("^[\"'`*]+|[\"'`*]+$", "").strip();
        for (String label : allowed) {
            if (label.equalsIgnoreCase(cleaned)) {
                return Optional.of(label);
            }
        }
        return Optional.empty();
    }
}
