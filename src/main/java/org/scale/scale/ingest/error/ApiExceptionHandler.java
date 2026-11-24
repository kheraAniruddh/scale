package org.scale.scale.ingest.error;

import org.scale.scale.classifier.LlmRejectedException;
import org.scale.scale.ingest.csv.CsvFormatException;
import org.scale.scale.ingest.model.InvalidRowException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Maps exceptions to RFC 9457 problem responses. Spring MVC's own exceptions (missing
 * part, unsupported media type, upload over the container limit, ...) keep their
 * standard 4xx mapping from {@link ResponseEntityExceptionHandler}.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(BadUploadException.class)
    public ProblemDetail badUpload(BadUploadException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(FileTooLargeException.class)
    public ProblemDetail tooLarge(FileTooLargeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONTENT_TOO_LARGE, e.getMessage());
    }

    @ExceptionHandler({CsvFormatException.class, InvalidRowException.class})
    public ProblemDetail invalidCsv(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage());
    }

    /** Passes the LLM service's status code through unchanged; its response body is not exposed. */
    @ExceptionHandler(LlmRejectedException.class)
    public ProblemDetail llmRejected(LlmRejectedException e) {
        log.warn("LLM rejected request: {}", e.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(e.statusCode()), e.getMessage());
    }

    @ExceptionHandler(StorageException.class)
    public ProblemDetail storage(StorageException e) {
        log.error("Storage failure", e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Could not store the ingested file");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail unexpected(Exception e) {
        log.error("Unhandled error", e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error");
    }
}
