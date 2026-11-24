package org.scale.scale.ingest.model;

import java.util.List;
import java.util.Map;

import org.scale.scale.ingest.csv.ParsedCsvRow;

/** Turns a generic CSV row into one typed, validated record. */
public interface RowMapper<T> {

    /** Columns that must be present in the header. Extra columns are ignored. */
    List<String> requiredHeaders();

    /** Column holding the row's unique identifier. */
    String idColumn();

    /** @throws InvalidRowException if a value is missing or malformed */
    T map(ParsedCsvRow row);

    /** Output value for each required column, keyed by column name. */
    Map<String, Object> columnValues(T value);
}
