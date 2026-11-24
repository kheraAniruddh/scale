package org.scale.scale.ingest;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * @param usersDir    directory that receives ingested user files
 * @param tasksDir    directory that receives ingested task files
 * @param maxFileSize largest accepted upload
 */
@ConfigurationProperties(prefix = "ingest")
public record IngestProperties(Path usersDir, Path tasksDir, DataSize maxFileSize) {

    public IngestProperties {
        if (usersDir == null || tasksDir == null || maxFileSize == null) {
            throw new IllegalArgumentException("ingest.users-dir, ingest.tasks-dir and ingest.max-file-size are required");
        }
    }
}
