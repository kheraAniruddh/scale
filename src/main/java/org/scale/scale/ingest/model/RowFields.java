package org.scale.scale.ingest.model;

import java.math.BigDecimal;

import org.scale.scale.ingest.csv.ParsedCsvRow;

/** Field rules shared by the row mappers. */
final class RowFields {

    private RowFields() {
    }

    static String requireText(ParsedCsvRow row, String column) {
        String value = row.get(column);
        if (value == null || value.isBlank()) {
            throw new InvalidRowException("Row " + row.rowNumber() + ": " + column + " is required");
        }
        return value;
    }

    /** Plain decimal number, finite and not negative. Rejects NaN, Infinity, hex and type suffixes. */
    static double requireNonNegativeNumber(ParsedCsvRow row, String column) {
        String text = requireText(row, column);
        double value;
        try {
            value = new BigDecimal(text).doubleValue();
        } catch (NumberFormatException e) {
            throw new InvalidRowException("Row " + row.rowNumber() + ": " + column + " must be a number, got '" + text + "'");
        }
        if (!Double.isFinite(value) || value < 0) {
            throw new InvalidRowException("Row " + row.rowNumber() + ": " + column + " must be a finite number >= 0, got '" + text + "'");
        }
        return value;
    }
}
