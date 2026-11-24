package org.scale.scale.ingest.csv;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.csv.CSVException;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

/**
 * Streams the data rows of one CSV file. Rows are read lazily, one at a time.
 * Iterating throws {@link CsvFormatException} on malformed input or a row whose
 * column count differs from the header.
 */
public class CsvRows implements Iterable<ParsedCsvRow>, AutoCloseable {

    private final CSVParser parser;
    private final List<String> headers;

    CsvRows(CSVParser parser, List<String> headers) {
        this.parser = parser;
        this.headers = List.copyOf(headers);
    }

    public List<String> headers() {
        return headers;
    }

    @Override
    public Iterator<ParsedCsvRow> iterator() {
        Iterator<CSVRecord> records = parser.iterator();
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                try {
                    return records.hasNext();
                } catch (UncheckedIOException e) {
                    throw translate(e);
                }
            }

            @Override
            public ParsedCsvRow next() {
                CSVRecord record;
                try {
                    record = records.next();
                } catch (UncheckedIOException e) {
                    throw translate(e);
                }
                return toRow(record);
            }
        };
    }

    private ParsedCsvRow toRow(CSVRecord record) {
        long rowNumber = record.getRecordNumber();
        if (record.size() != headers.size()) {
            throw new CsvFormatException("Row " + rowNumber + " has " + record.size()
                    + " values but the header has " + headers.size() + " columns");
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            values.put(headers.get(i), record.get(i));
        }
        return new ParsedCsvRow(headers, values, rowNumber);
    }

    private static RuntimeException translate(UncheckedIOException e) {
        if (e.getCause() instanceof CSVException csvException) {
            return new CsvFormatException("Malformed CSV: " + csvException.getMessage(), e);
        }
        return e;
    }

    @Override
    public void close() throws IOException {
        parser.close();
    }
}
