package org.scale.scale.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.scale.scale.ingest.error.BadUploadException;

class OutputFileNamesTest {

    @ParameterizedTest
    @CsvSource({
            "users.csv, users.json",
            "users, users.json",
            "my.users.csv, my.users.json",
            "../../etc/passwd.csv, passwd.json",
            "C:\\\\tmp\\\\tasks.csv, tasks.json",
            "a b$c.csv, a_b_c.json",
            ".hidden, .hidden.json"
    })
    void derivesSafeJsonName(String input, String expected) {
        assertThat(OutputFileNames.jsonNameFor(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "..", "dir/", "../"})
    void rejectsUnusableNames(String input) {
        assertThatThrownBy(() -> OutputFileNames.jsonNameFor(input)).isInstanceOf(BadUploadException.class);
    }

    @Test
    void rejectsNullName() {
        assertThatThrownBy(() -> OutputFileNames.jsonNameFor(null)).isInstanceOf(BadUploadException.class);
    }
}
