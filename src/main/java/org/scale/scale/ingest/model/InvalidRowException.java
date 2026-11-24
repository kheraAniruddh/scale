package org.scale.scale.ingest.model;

/** A CSV row parsed fine but its values break a field rule. */
public class InvalidRowException extends RuntimeException {

    public InvalidRowException(String message) {
        super(message);
    }
}
