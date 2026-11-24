package org.scale.scale.ingest.error;

/** The upload itself is unusable: missing, empty, or without a usable file name. */
public class BadUploadException extends RuntimeException {

    public BadUploadException(String message) {
        super(message);
    }
}
