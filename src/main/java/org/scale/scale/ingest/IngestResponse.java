package org.scale.scale.ingest;

/**
 * @param rowsIngested rows written to the output JSON file
 * @param rowsDropped  rows left out because the LLM could not label them after retries
 */
public record IngestResponse(int rowsIngested, int rowsDropped) {
}
