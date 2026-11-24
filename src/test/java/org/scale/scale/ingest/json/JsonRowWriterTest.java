package org.scale.scale.ingest.json;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class JsonRowWriterTest {

    private final JsonRowWriter writer = new JsonRowWriter(JsonMapper.builder().build());

    @TempDir
    Path dir;

    @Test
    void commitPublishesJsonArrayAndRemovesTemp() throws IOException {
        try (JsonRowWriter.Session session = writer.open(dir.resolve("out"), "rows.json")) {
            session.write(Map.of("id", "1"));
            session.write(Map.of("id", "2"));
            session.commit();
        }

        assertThat(Files.readString(dir.resolve("out/rows.json"))).isEqualTo("[{\"id\":\"1\"},{\"id\":\"2\"}]");
        assertThat(listNames(dir.resolve("out"))).containsExactly("rows.json");
    }

    @Test
    void closeWithoutCommitLeavesNothingBehind() throws IOException {
        try (JsonRowWriter.Session session = writer.open(dir, "rows.json")) {
            session.write(Map.of("id", "1"));
        }

        assertThat(listNames(dir)).isEmpty();
    }

    @Test
    void abandonedWriteKeepsPreviousFile() throws IOException {
        Files.writeString(dir.resolve("rows.json"), "[\"old\"]");

        try (JsonRowWriter.Session session = writer.open(dir, "rows.json")) {
            session.write("new");
        }

        assertThat(Files.readString(dir.resolve("rows.json"))).isEqualTo("[\"old\"]");
        assertThat(listNames(dir)).containsExactly("rows.json");
    }

    @Test
    void commitReplacesExistingFile() throws IOException {
        Files.writeString(dir.resolve("rows.json"), "[\"old\"]");

        try (JsonRowWriter.Session session = writer.open(dir, "rows.json")) {
            session.write("new");
            session.commit();
        }

        assertThat(Files.readString(dir.resolve("rows.json"))).isEqualTo("[\"new\"]");
    }

    @Test
    void emptySessionWritesEmptyArray() throws IOException {
        try (JsonRowWriter.Session session = writer.open(dir, "rows.json")) {
            session.commit();
        }

        assertThat(Files.readString(dir.resolve("rows.json"))).isEqualTo("[]");
    }

    private static java.util.List<String> listNames(Path path) throws IOException {
        try (Stream<Path> files = Files.list(path)) {
            return files.map(p -> p.getFileName().toString()).toList();
        }
    }
}
