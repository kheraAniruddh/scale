package org.scale.scale.ingest;

import org.scale.scale.ingest.error.BadUploadException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/v1/ingest")
public class IngestController {

    static final String FILE_PART = "file";

    private final IngestService ingestService;

    public IngestController(IngestService ingestService) {
        this.ingestService = ingestService;
    }

    @PostMapping(path = "/users", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<IngestResponse> ingestUsers(@RequestPart(FILE_PART) MultipartFile file) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ingestService.ingestUsers(toUpload(file)));
    }

    @PostMapping(path = "/tasks", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<IngestResponse> ingestTasks(@RequestPart(FILE_PART) MultipartFile file) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ingestService.ingestTasks(toUpload(file)));
    }

    private static CsvUpload toUpload(MultipartFile file) {
        if (file.isEmpty()) {
            throw new BadUploadException("Uploaded file is empty");
        }
        return new CsvUpload(file.getOriginalFilename(), file.getSize(), file);
    }
}
