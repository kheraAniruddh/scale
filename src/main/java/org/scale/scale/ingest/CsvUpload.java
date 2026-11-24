package org.scale.scale.ingest;

import org.springframework.core.io.InputStreamSource;

/** An uploaded CSV, decoupled from the HTTP multipart type. */
public record CsvUpload(String originalFileName, long sizeBytes, InputStreamSource content) {
}
