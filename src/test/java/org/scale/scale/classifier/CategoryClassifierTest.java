package org.scale.scale.classifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.scale.scale.ingest.model.Task;
import org.scale.scale.ingest.model.User;

class CategoryClassifierTest {

    private static final ClassifierProperties PROPERTIES = new ClassifierProperties(
            List.of("Legal", "Automotive", "Geography", "Healthcare", "Math", "Music"),
            List.of("Mechanic/Technician", "Healthcare Professional", "Legal Professional", "Software Developer", "Other", "Student"),
            3, Duration.ofMillis(500));

    private static final Task TASK = new Task("t1", 1.0, 2.0, "where is meerut");
    private static final User USER = new User("u1", "BSc Nursing", "5 years ICU nurse", 10.0);

    private final ScriptedLlm llm = new ScriptedLlm();
    private final List<Duration> sleeps = new ArrayList<>();
    private final CategoryClassifier classifier = new CategoryClassifier(llm, PROPERTIES, sleeps::add);

    @Test
    void returnsAllowedTaskCategory() {
        llm.reply("Geography");

        assertThat(classifier.classifyTask(TASK)).contains("Geography");
        assertThat(llm.prompts).hasSize(1);
        assertThat(llm.prompts.getFirst()).contains("where is meerut").contains("Legal, Automotive, Geography, Healthcare, Math, Music");
        assertThat(sleeps).isEmpty();
    }

    @Test
    void returnsAllowedUserSpecialization() {
        llm.reply("Healthcare Professional");

        assertThat(classifier.classifyUser(USER)).contains("Healthcare Professional");
        assertThat(llm.prompts.getFirst()).contains("BSc Nursing").contains("5 years ICU nurse");
    }

    @ParameterizedTest
    @ValueSource(strings = {"legal", "  Legal\n", "\"Legal\"", "Legal.", "**Legal**", "`legal`"})
    void normalizesReplyToConfiguredLabel(String reply) {
        llm.reply(reply);

        assertThat(classifier.classifyTask(TASK)).contains("Legal");
    }

    @Test
    void retriesTransientErrorWithExponentialBackoffThenSucceeds() {
        llm.fail().fail().reply("Math");

        assertThat(classifier.classifyTask(TASK)).contains("Math");
        assertThat(llm.prompts).hasSize(3);
        assertThat(sleeps).containsExactly(Duration.ofMillis(500), Duration.ofMillis(1000));
    }

    @Test
    void dropsRowAfterRetriesAreExhausted() {
        llm.fail().fail().fail().fail();

        assertThat(classifier.classifyTask(TASK)).isEmpty();
        assertThat(llm.prompts).hasSize(4);
        assertThat(sleeps).containsExactly(Duration.ofMillis(500), Duration.ofMillis(1000), Duration.ofMillis(2000));
    }

    @Test
    void labelOutsideAllowedListIsRetried() {
        llm.reply("Science").reply("Math");

        assertThat(classifier.classifyTask(TASK)).contains("Math");
        assertThat(llm.prompts).hasSize(2);
    }

    @Test
    void labelOutsideAllowedListEveryTimeDropsRow() {
        llm.reply("Science").reply("Physics").reply("Chemistry").reply("Biology");

        assertThat(classifier.classifyUser(USER)).isEmpty();
        assertThat(llm.prompts).hasSize(4);
    }

    @Test
    void taskCategoryIsNotAcceptedForUserAndViceVersa() {
        llm.reply("Legal").reply("Legal").reply("Legal").reply("Legal");
        assertThat(classifier.classifyUser(USER)).isEmpty();

        llm.reply("Student").reply("Student").reply("Student").reply("Student");
        assertThat(classifier.classifyTask(TASK)).isEmpty();
    }

    @Test
    void rejectedRequestIsNotRetriedAndPropagates() {
        llm.reject(401);

        assertThatThrownBy(() -> classifier.classifyTask(TASK))
                .isInstanceOf(LlmRejectedException.class)
                .extracting(e -> ((LlmRejectedException) e).statusCode()).isEqualTo(401);
        assertThat(llm.prompts).hasSize(1);
        assertThat(sleeps).isEmpty();
    }

    @Test
    void zeroRetriesMeansOneAttempt() {
        ClassifierProperties noRetry = new ClassifierProperties(PROPERTIES.taskCategories(), PROPERTIES.userSpecializations(), 0, Duration.ZERO);
        CategoryClassifier oneShot = new CategoryClassifier(llm, noRetry, sleeps::add);
        llm.fail();

        assertThat(oneShot.classifyTask(TASK)).isEmpty();
        assertThat(llm.prompts).hasSize(1);
    }

    /** Fake LLM that plays back a script of replies and failures; fails if called past the script. */
    private static final class ScriptedLlm implements LlmClient {

        final List<String> prompts = new ArrayList<>();
        private final Deque<Supplier<String>> script = new ArrayDeque<>();

        ScriptedLlm reply(String text) {
            script.add(() -> text);
            return this;
        }

        ScriptedLlm fail() {
            script.add(() -> {
                throw new TransientLlmException("503");
            });
            return this;
        }

        ScriptedLlm reject(int status) {
            script.add(() -> {
                throw new LlmRejectedException(status, null);
            });
            return this;
        }

        @Override
        public String complete(String prompt) {
            prompts.add(prompt);
            Supplier<String> next = script.poll();
            if (next == null) {
                throw new AssertionError("Unexpected LLM call #" + prompts.size());
            }
            return next.get();
        }
    }
}
