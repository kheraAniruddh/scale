# Task:
Create two endpoints
1. POST v1/ingest/users which takes multipart file in the request and convert into json object and save it on server side under predefined configurable path on the server
request body example:

User {

User_ID: unique identifier

Education: text description of academic background

Experience: text description of work history

Available_Time: float (expected amount of time to work)

}
response -> number of rows ingested, http code 202 if successful
2. POST v1/ingest/tasks which takes multipart file in the request and convert into json object and save it on server side under predefined configurable path on the server. 
request body example
Task {

   Task_ID: unique identifier

   Time_To_Complete: float (elapsed time)

   Deadline: float (need to complete by this time)

   Task_String: text description of the required work

}
response -> number of rows ingested, http code 202 if successful


# Clarifications & Assumptions:
1. bad csv row - fail the whole request
2. file size - 5MB max limit

# Design & Decisions:
1. structure the code in components
   1. Controller - REST APIs, mapping, and request_body validation. All are required fields as given in samplw
   2. Service - orchestrates the parsing and validating the csv rows, calling the JsonRowWriter to convert the csv row into json object
   3. CsvParser- this is a generic csv parser parse(multipart file) -> ParsedCsvRow{list<header>, map<string,string> values
      1. end-to-end stream rows
   4. jsonRowWriter - converts streamed json rows into json object
      1. atomic writes - copy-on-write using .tmp files and then move to the output dir with file name as the {input_file_name}.json
      2. clear .tmp files
   5. apiErrrorExceptions -
    1. 413- file too large
    2. 400 - bad request
    3. 422 - for csv parsing error
   
# Implementation:
1. use sprinboot, mvn project add dependency apache.commons handling the column values contianing ',' ,'\n', quotes
# Tests:
1. happy path
2. 413- file too large
3. 400 - bad request
4. 422 - for csv parsing error

# Decisions (added during implementation):
1. Multipart part name is `file`. Response body is `{"rowsIngested": <n>}` with 202. Errors are RFC 9457 problem JSON (`status`, `title`, `detail`), no stack traces.
2. Output file is a JSON array of row objects, keys exactly as the CSV headers (`User_ID`, `Education`, ...). Numbers are JSON numbers (double, e.g. `3` -> `3.0`, `1e3` -> `1000.0`).
   Output is deterministic: rows keep the CSV row order, and each object's keys keep the CSV header's column order. Compact JSON (no pretty-printing). The same input always gives a byte-identical file.
3. Config (`application.properties`): `ingest.users-dir` (default `./data/users`), `ingest.tasks-dir` (default `./data/tasks`), `ingest.max-file-size` (default `5MB`). Spring's multipart limit follows `ingest.max-file-size`.
4. Output file name is `{input_file_name without extension}.json`. Path parts are stripped, unsafe characters become `_`. Re-uploading the same name replaces the earlier file atomically.
5. `.tmp` file is created in the output dir itself (same filesystem, so the move is atomic) and deleted when the request fails.
6. Header columns may be in any order (output follows that order); extra columns are dropped from the output. Header-only file is accepted: 202, 0 rows, `[]` written.
7. Status mapping:
   - 400: missing `file` part, empty (0-byte) file, unusable file name.
   - 413: file larger than `ingest.max-file-size` (checked in the service, and by the container).
   - 422: malformed CSV (unclosed quote etc.), wrong column count in a row, missing/duplicate/blank header, missing required column, blank required value, non-numeric / negative / NaN / Infinity number, duplicate `User_ID` / `Task_ID` in the same file.
   - 415: non-multipart request (Spring default). 500 only for server-side storage failures.
8. Values are trimmed. Numbers must be plain decimals >= 0 (`1`, `2.5`, `1e3`); `NaN`, `Infinity`, hex, `1f` are rejected.
9. Deviation: the service takes a `CsvUpload(fileName, size, InputStreamSource)` record instead of `MultipartFile`, and `CsvParser.parse` takes an `InputStream`, so services don't depend on HTTP types (CLAUDE.md). `ParsedCsvRow` also carries `rowNumber` for error messages.
10. No check that `Deadline >= Time_To_Complete` (not asked).


# Additional tests (added during implementation):
- CsvParserTest: quoted commas / newlines / escaped quotes, BOM + blank lines, trimming, wrong column count, unclosed quote, duplicate header, blank header name, no header row.
- JsonRowWriterTest: commit writes array and removes tmp, abandoned write leaves no files, abandoned write keeps previous file, commit replaces existing file, empty array.
- OutputFileNamesTest: safe name derivation, path traversal, rejected names.
- IngestControllerTest: users + tasks happy paths (checks JSON on disk), output keeps CSV column order and drops extra columns (exact bytes), output keeps CSV row order, same input gives byte-identical output, header-only file, 413, 400 (missing part, empty file, no name), 415 non-multipart, 422 (malformed, column count, missing column, blank value, non-numeric, negative/NaN/overflow, duplicate id), bad row after good rows fails the whole request and keeps previous output.
