package org.scale.scale.ingest.csv;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.DuplicateHeaderMode;
import org.springframework.stereotype.Component;

/**
 * Generic CSV reader. The first record is the header. Handles quoted values that
 * contain commas, newlines and escaped quotes (RFC 4180). Input is UTF-8; a leading
 * byte-order mark is skipped.
 */
@Component
public class CsvParser {

    private static final char BOM = '﻿';

    private static final CSVFormat FORMAT = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setTrim(true)
            .setIgnoreEmptyLines(true)
            .setAllowMissingColumnNames(false)
            .setDuplicateHeaderMode(DuplicateHeaderMode.DISALLOW)
            .get();

    /** Opens the stream for reading. The caller must close the returned {@link CsvRows}. */
    public CsvRows parse(InputStream in) throws IOException {
        Reader reader = skipBom(new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)));
        CSVParser parser;
        try {
            parser = CSVParser.parse(reader, FORMAT);
        } catch (IllegalArgumentException e) {
            reader.close();
            throw new CsvFormatException("Invalid CSV header: " + e.getMessage(), e);
        } catch (UncheckedIOException e) {
            reader.close();
            throw new CsvFormatException("Malformed CSV header: " + e.getMessage(), e);
        }
        List<String> headers = parser.getHeaderNames();
        if (headers.isEmpty()) {
            parser.close();
            throw new CsvFormatException("CSV has no header row");
        }
        return new CsvRows(parser, headers);
    }

    private static Reader skipBom(BufferedReader reader) throws IOException {
        reader.mark(1);
        int first = reader.read();
        if (first != BOM) {
            reader.reset();
        }
        return reader;
    }
}
