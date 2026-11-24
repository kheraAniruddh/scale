package org.scale.scale.ingest.error;

/** Writing the output file on the server failed. */
public class StorageException extends RuntimeException {

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
