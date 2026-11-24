package org.scale.scale.ingest.csv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class CsvParserTest {

    private final CsvParser parser = new CsvParser();

    private List<ParsedCsvRow> readAll(String csv) throws IOException {
        List<ParsedCsvRow> result = new ArrayList<>();
        try (CsvRows rows = parser.parse(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)))) {
            for (ParsedCsvRow row : rows) {
                result.add(row);
            }
        }
        return result;
    }

    @Test
    void readsHeaderAndRows() throws IOException {
        List<ParsedCsvRow> rows = readAll("a,b\n1,2\n3,4\n");

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).headers()).containsExactly("a", "b");
        assertThat(rows.get(0).values()).containsEntry("a", "1").containsEntry("b", "2");
        assertThat(rows.get(1).rowNumber()).isEqualTo(2);
    }

    @Test
    void handlesQuotedCommasNewlinesAndEscapedQuotes() throws IOException {
        List<ParsedCsvRow> rows = readAll("a,b\n\"x, y\",\"line1\nline2 \"\"quoted\"\"\"\n");

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("a")).isEqualTo("x, y");
        assertThat(rows.get(0).get("b")).isEqualTo("line1\nline2 \"quoted\"");
    }

    @Test
    void skipsByteOrderMarkAndBlankLines() throws IOException {
        List<ParsedCsvRow> rows = readAll("\uFEFFa,b\r\n\r\n1,2\r\n");

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).headers()).containsExactly("a", "b");
    }

    @Test
    void trimsUnquotedWhitespace() throws IOException {
        assertThat(readAll("a , b\n 1 , 2 \n").get(0).values()).containsEntry("a", "1").containsEntry("b", "2");
    }

    @Test
    void rejectsRowWithWrongColumnCount() {
        assertThatThrownBy(() -> readAll("a,b\n1,2\n3\n"))
                .isInstanceOf(CsvFormatException.class)
                .hasMessageContaining("Row 2");
    }

    @Test
    void rejectsUnterminatedQuote() {
        assertThatThrownBy(() -> readAll("a,b\n1,\"open\n"))
                .isInstanceOf(CsvFormatException.class)
                .hasMessageContaining("Malformed CSV");
    }

    @Test
    void rejectsDuplicateHeader() {
        assertThatThrownBy(() -> readAll("a,a\n1,2\n")).isInstanceOf(CsvFormatException.class);
    }

    @Test
    void rejectsBlankHeaderName() {
        assertThatThrownBy(() -> readAll("a,,c\n1,2,3\n")).isInstanceOf(CsvFormatException.class);
    }

    @Test
    void rejectsMissingHeaderRow() {
        assertThatThrownBy(() -> readAll("\n\n")).isInstanceOf(CsvFormatException.class);
    }
}
