package org.scale.scale.ingest.csv;

/** The uploaded content is not valid CSV, or a row does not match the header. */
public class CsvFormatException extends RuntimeException {

    public CsvFormatException(String message) {
        super(message);
    }

    public CsvFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
