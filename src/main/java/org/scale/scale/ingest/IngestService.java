package org.scale.scale.ingest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import org.scale.scale.classifier.CategoryClassifier;
import org.scale.scale.ingest.csv.CsvFormatException;
import org.scale.scale.ingest.csv.CsvParser;
import org.scale.scale.ingest.csv.CsvRows;
import org.scale.scale.ingest.csv.ParsedCsvRow;
import org.scale.scale.ingest.error.FileTooLargeException;
import org.scale.scale.ingest.error.StorageException;
import org.scale.scale.ingest.json.JsonRowWriter;
import org.scale.scale.ingest.model.InvalidRowException;
import org.scale.scale.ingest.model.RowMapper;
import org.scale.scale.ingest.model.Task;
import org.scale.scale.ingest.model.TaskRowMapper;
import org.scale.scale.ingest.model.User;
import org.scale.scale.ingest.model.UserRowMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Streams an uploaded CSV row by row: parse, validate, label with the LLM, write JSON.
 * Output rows keep the file's row order and each row's keys keep the header's column
 * order, with the label column last. Any bad row fails the whole upload and no output
 * file is left behind. A row the LLM cannot label after retries is dropped and counted.
 */
@Service
public class IngestService {

    private static final Logger log = LoggerFactory.getLogger(IngestService.class);

    static final String TASK_LABEL_COLUMN = "Category";
    static final String USER_LABEL_COLUMN = "Specialization";

    private final CsvParser csvParser;
    private final JsonRowWriter jsonRowWriter;
    private final UserRowMapper userRowMapper;
    private final TaskRowMapper taskRowMapper;
    private final CategoryClassifier classifier;
    private final IngestProperties properties;

    public IngestService(CsvParser csvParser, JsonRowWriter jsonRowWriter, UserRowMapper userRowMapper,
                         TaskRowMapper taskRowMapper, CategoryClassifier classifier, IngestProperties properties) {
        this.csvParser = csvParser;
        this.jsonRowWriter = jsonRowWriter;
        this.userRowMapper = userRowMapper;
        this.taskRowMapper = taskRowMapper;
        this.classifier = classifier;
        this.properties = properties;
    }

    public IngestResponse ingestUsers(CsvUpload upload) {
        return ingest(upload, userRowMapper, classifier::classifyUser, USER_LABEL_COLUMN, properties.usersDir());
    }

    public IngestResponse ingestTasks(CsvUpload upload) {
        return ingest(upload, taskRowMapper, classifier::classifyTask, TASK_LABEL_COLUMN, properties.tasksDir());
    }

    private <T> IngestResponse ingest(CsvUpload upload, RowMapper<T> mapper, Function<T, Optional<String>> labeler,
                                      String labelColumn, Path outputDir) {
        checkSize(upload);
        String outputFileName = OutputFileNames.jsonNameFor(upload.originalFileName());

        try (InputStream in = upload.content().getInputStream();
             CsvRows rows = csvParser.parse(in)) {
            checkHeaders(rows.headers(), mapper.requiredHeaders());
            try (JsonRowWriter.Session out = jsonRowWriter.open(outputDir, outputFileName)) {
                IngestResponse result = writeRows(rows, mapper, labeler, labelColumn, out);
                Path written = out.commit();
                log.info("Ingested {} rows into {}, dropped {}", result.rowsIngested(), written, result.rowsDropped());
                return result;
            }
        } catch (IOException e) {
            throw new StorageException("Could not ingest file " + outputFileName, e);
        }
    }

    private static <T> IngestResponse writeRows(CsvRows rows, RowMapper<T> mapper, Function<T, Optional<String>> labeler,
                                                String labelColumn, JsonRowWriter.Session out) {
        Set<String> seenIds = new HashSet<>();
        int count = 0;
        int dropped = 0;
        for (ParsedCsvRow row : rows) {
            T value = mapper.map(row);
            String id = row.get(mapper.idColumn());
            if (!seenIds.add(id)) {
                throw new InvalidRowException("Row " + row.rowNumber() + ": duplicate " + mapper.idColumn() + " '" + id + "'");
            }
            Optional<String> label = labeler.apply(value);
            if (label.isEmpty()) {
                dropped++;
                continue;
            }
            Map<String, Object> json = inHeaderOrder(row.headers(), mapper.columnValues(value));
            json.put(labelColumn, label.get());
            out.write(json);
            count++;
        }
        return new IngestResponse(count, dropped);
    }

    /**
     * Orders a row's output keys the way the CSV header orders its columns, so the JSON
     * file follows the input's row and column order. Columns the type doesn't use are dropped.
     */
    private static Map<String, Object> inHeaderOrder(List<String> headers, Map<String, Object> columnValues) {
        Map<String, Object> ordered = new LinkedHashMap<>();
        for (String header : headers) {
            if (columnValues.containsKey(header)) {
                ordered.put(header, columnValues.get(header));
            }
        }
        return ordered;
    }

    private void checkSize(CsvUpload upload) {
        long max = properties.maxFileSize().toBytes();
        if (upload.sizeBytes() > max) {
            throw new FileTooLargeException("File is " + upload.sizeBytes() + " bytes; the limit is " + max + " bytes");
        }
    }

    private static void checkHeaders(List<String> actual, List<String> required) {
        List<String> missing = new ArrayList<>();
        for (String column : required) {
            if (!actual.contains(column)) {
                missing.add(column);
            }
        }
        if (!missing.isEmpty()) {
            throw new CsvFormatException("Missing required columns " + missing + "; found " + actual);
        }
    }
}
