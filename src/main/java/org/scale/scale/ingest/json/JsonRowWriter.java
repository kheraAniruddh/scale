package org.scale.scale.ingest.json;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.SequenceWriter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes a stream of rows as one JSON array file. Rows go to a {@code .tmp} file in
 * the target directory; {@link Session#commit()} moves it atomically to its final
 * name. A session closed without commit deletes its temp file, so readers never see
 * a partial file.
 */
@Component
public class JsonRowWriter {

    private static final Logger log = LoggerFactory.getLogger(JsonRowWriter.class);

    private final JsonMapper jsonMapper;

    public JsonRowWriter(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    /** Starts writing {@code dir/fileName}. Creates {@code dir} if needed. */
    public Session open(Path dir, String fileName) throws IOException {
        Files.createDirectories(dir);
        Path target = dir.resolve(fileName);
        Path tmp = Files.createTempFile(dir, fileName + ".", ".tmp");
        try {
            OutputStream out = new BufferedOutputStream(Files.newOutputStream(tmp));
            SequenceWriter sequence = jsonMapper.writer().writeValuesAsArray(out);
            return new Session(sequence, tmp, target);
        } catch (IOException | JacksonException e) {
            Files.deleteIfExists(tmp);
            throw e;
        }
    }

    /** One in-progress output file. Not thread-safe. */
    public static final class Session implements AutoCloseable {

        private final SequenceWriter sequence;
        private final Path tmp;
        private final Path target;
        private boolean committed;

        private Session(SequenceWriter sequence, Path tmp, Path target) {
            this.sequence = sequence;
            this.tmp = tmp;
            this.target = target;
        }

        public void write(Object row) {
            sequence.write(row);
        }

        /** Finishes the JSON array and publishes the file under its final name. */
        public Path commit() throws IOException {
            sequence.close();
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            committed = true;
            return target;
        }

        @Override
        public void close() {
            if (committed) {
                return;
            }
            try {
                sequence.close();
            } catch (JacksonException e) {
                log.debug("Ignoring error closing abandoned output {}", tmp, e);
            }
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException e) {
                log.warn("Could not delete temp file {}", tmp, e);
            }
        }
    }
}
