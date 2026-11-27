
# Task:
1. use url = "https://litellm-proxy.ml.scale.com/v1/chat/completions"
   headers = {
   "Content-Type": "application/json",
   "Authorization": "Bearer -zxxxxxxxxxxxxxx"
   }
   data = {
   "model": "openai/gpt-4o",
   "messages": [
   {
   "role": "user",
   "content": "Hello!"
   }
   ]
   } 
to categorize users and tasks based on the parse json object by the parser. Add column category for task and specialization for user.
   Task Categories include: Legal, Automotive, Geography, Healthcare, Math, Music
   User Specializations include: Mechanic/Technician, Healthcare Professional, Legal Professional, Software Developer, Other, Student

# Clarifications & Assumptions:
1. Ensure the task category returned by llm is as mentioned in task allowed task categories
2. Ensure the user Specializations returned by llm is as mentioned in user allowed user Specializations
3. if any transient error retry up to max 3 times with exponential backing off - drop the row

# Design & Decisions:
1.  add new component package in extension to part1, as classifier, have llm interface and then concrete class for using the above llm http client. keep the configuration in application.properties for being configurable
2. keep the allowed task and user categories in  application.properties for being configurable
3. for LLM API_SECRET_KEY in .env, configure it in application.properties for being configurable
4. add max retry,timeout as well in application.properties for being configurable
5. the classifier once get response from the llm verfies the category/specialization as configured in properties file
# Implementation:


# Tests:
1. happy path
2. transient error retry
3. transient error drop the row


post /jobs -> job_id -for traking result

# Decisions (added during implementation, confirmed with user):
1. Package `org.scale.scale.classifier`: `LlmClient` interface, `LiteLlmClient` (Spring RestClient) concrete class, `CategoryClassifier` (prompting, retry, label check), `LlmProperties`, `ClassifierProperties`, `ClassifierConfig`, `Sleeper`.
2. HTTP client: Spring RestClient (already in pom). The unused `openai-java` dependency was removed.
3. One LLM call per row, sequential, `temperature: 0`. Task prompt uses `Task_String`; user prompt uses `Education` + `Experience`.
4. Output JSON gets one extra key, appended after the CSV columns: `Category` for tasks, `Specialization` for users.
5. Label check: reply is trimmed, surrounding quotes/backticks/`*` and a trailing `.` are stripped, then matched case-insensitively against the configured list; the configured spelling is written.
6. Transient = HTTP 408, 429, 5xx, timeout/connection error, unreadable or empty reply, **or a label not in the allowed list**. Retried up to `classifier.max-retries` (3) times after the first attempt (4 attempts total), backoff `initial-backoff` x 1, 2, 4 (500ms, 1s, 2s). After that the row is dropped and counted.
7. Non-transient LLM errors (other 4xx, e.g. 400/401/403) are not retried: the request fails with **the same status code**, problem JSON detail `LLM request rejected with status N` (the LLM's response body is not exposed), and no output file is written.
8. Response is now `{"rowsIngested": n, "rowsDropped": m}`, still 202. `rowsIngested` = rows written to the file. If every row is dropped, the file is `[]`.
9. CSV and row validation (part 1) still runs first; a 422 row fails the request as before. Duplicate IDs are checked before the LLM call.
10. Config in `application.properties`: `llm.url`, `llm.model`, `llm.api-key=${API_SECRET_KEY:}`, `llm.timeout=10s`, `classifier.max-retries=3`, `classifier.initial-backoff=500ms`, `classifier.task-categories`, `classifier.user-specializations`.
11. The API key lives in `.env` (`API_SECRET_KEY=...`, gitignored), loaded via `spring.config.import=optional:file:.env[.properties]`. A real environment variable `API_SECRET_KEY` also works. The app refuses to start without a key. `LlmProperties.toString()` masks the key.

# Additional tests (added during implementation):
- CategoryClassifierTest: happy path (task + user), reply normalization, transient retry with backoff 500ms/1s, drop after 4 attempts with backoff 500ms/1s/2s, unknown label retried, unknown label every time drops, task label not accepted for user and vice versa, 4xx rejection not retried, max-retries 0.
- LiteLlmClientTest (MockRestServiceServer, no network): request URL/headers/body, 408/429/5xx transient, 400/401/403/404/422 rejected with same status and no body leak, timeout, connection failure, unreadable body, empty choices, missing content, key masked in toString.
- IngestControllerTest (LLM mocked): label appended to output, transient retry keeps row, exhausted retries drop only that row, unknown label drops row, all rows dropped writes `[]`, 401 from LLM propagates as 401 with no file, invalid CSV makes no LLM call. Part 1 exact-output tests updated for the new label key.
