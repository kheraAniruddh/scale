package org.scale.scale.ingest.csv;

import java.util.List;
import java.util.Map;

/**
 * One data row of a CSV file.
 *
 * @param headers   column names, in file order
 * @param values    column name to trimmed cell value
 * @param rowNumber 1-based data row number (header row not counted)
 */
public record ParsedCsvRow(List<String> headers, Map<String, String> values, long rowNumber) {

    public ParsedCsvRow {
        headers = List.copyOf(headers);
        values = Map.copyOf(values);
    }

    public String get(String column) {
        return values.get(column);
    }
}
