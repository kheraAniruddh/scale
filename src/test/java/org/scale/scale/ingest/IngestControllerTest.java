package org.scale.scale.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.scale.scale.classifier.LlmClient;
import org.scale.scale.classifier.LlmRejectedException;
import org.scale.scale.classifier.Sleeper;
import org.scale.scale.classifier.TransientLlmException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties = "llm.api-key=test-key")
@AutoConfigureMockMvc
class IngestControllerTest {

    private static final Path ROOT = createRoot();
    private static final Path USERS_DIR = ROOT.resolve("users");
    private static final Path TASKS_DIR = ROOT.resolve("tasks");

    private static final String USERS_HEADER = "User_ID,Education,Experience,Available_Time\n";
    private static final String TASKS_HEADER = "Task_ID,Time_To_Complete,Deadline,Task_String\n";

    @DynamicPropertySource
    static void dirs(DynamicPropertyRegistry registry) {
        registry.add("ingest.users-dir", USERS_DIR::toString);
        registry.add("ingest.tasks-dir", TASKS_DIR::toString);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    /** Stubbed LLM: no test touches the network. */
    @MockitoBean
    private LlmClient llmClient;

    /** No-op sleeper: retries never wait. */
    @MockitoBean
    private Sleeper sleeper;

    @BeforeEach
    void setUp() throws IOException {
        deleteContents(ROOT);
        when(llmClient.complete(startsWith("Classify this task"))).thenReturn("Legal");
        when(llmClient.complete(startsWith("Classify this user"))).thenReturn("Student");
    }

    // ---- happy paths ----

    @Test
    void ingestsUsersAndWritesJson() throws Exception {
        String csv = USERS_HEADER
                + "u1,\"BSc, Computer Science\",\"5 years\nbackend \"\"Java\"\"\",20.5\n"
                + "u2,PhD Physics,None,0\n";

        upload("/v1/ingest/users", "users.csv", csv)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.rowsIngested").value(2))
                .andExpect(jsonPath("$.rowsDropped").value(0));

        JsonNode json = jsonMapper.readTree(USERS_DIR.resolve("users.json").toFile());
        assertThat(json.size()).isEqualTo(2);
        assertThat(json.get(0).get("User_ID").asString()).isEqualTo("u1");
        assertThat(json.get(0).get("Education").asString()).isEqualTo("BSc, Computer Science");
        assertThat(json.get(0).get("Experience").asString()).isEqualTo("5 years\nbackend \"Java\"");
        assertThat(json.get(0).get("Available_Time").asDouble()).isEqualTo(20.5);
        assertThat(json.get(1).get("Available_Time").asDouble()).isZero();
        assertThat(json.get(0).get("Specialization").asString()).isEqualTo("Student");
        assertThat(listNames(USERS_DIR)).containsExactly("users.json");
    }

    @Test
    void ingestsTasksAndWritesJson() throws Exception {
        String csv = TASKS_HEADER + "t1,2.5,10,\"Label images, then review\"\n";

        upload("/v1/ingest/tasks", "tasks.csv", csv)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.rowsIngested").value(1));

        JsonNode json = jsonMapper.readTree(TASKS_DIR.resolve("tasks.json").toFile());
        assertThat(json.get(0).get("Task_ID").asString()).isEqualTo("t1");
        assertThat(json.get(0).get("Time_To_Complete").asDouble()).isEqualTo(2.5);
        assertThat(json.get(0).get("Deadline").asDouble()).isEqualTo(10.0);
        assertThat(json.get(0).get("Task_String").asString()).isEqualTo("Label images, then review");
        assertThat(json.get(0).get("Category").asString()).isEqualTo("Legal");
    }

    @Test
    void outputKeepsCsvColumnOrderDropsExtraColumnsAndAppendsLabel() throws Exception {
        String csv = "Available_Time,Notes,Experience,Education,User_ID\n3,extra,Exp,Edu,u1\n";

        upload("/v1/ingest/users", "users.csv", csv).andExpect(status().isAccepted());

        assertThat(Files.readString(USERS_DIR.resolve("users.json"))).isEqualTo(
                "[{\"Available_Time\":3.0,\"Experience\":\"Exp\",\"Education\":\"Edu\",\"User_ID\":\"u1\",\"Specialization\":\"Student\"}]");
    }

    @Test
    void outputKeepsCsvRowOrder() throws Exception {
        String csv = TASKS_HEADER + "t3,1,2,c\nt1,1,2,a\nt2,1,2,b\n";

        upload("/v1/ingest/tasks", "tasks.csv", csv).andExpect(status().isAccepted());

        JsonNode json = jsonMapper.readTree(TASKS_DIR.resolve("tasks.json").toFile());
        List<String> ids = new ArrayList<>();
        for (JsonNode row : json) {
            ids.add(row.get("Task_ID").asString());
        }
        assertThat(ids).containsExactly("t3", "t1", "t2");
    }

    @Test
    void sameInputGivesByteIdenticalOutput() throws Exception {
        String csv = TASKS_HEADER + "t1,2.5,10,\"Label, then \"\"review\"\"\"\nt2,0,1e3,second\n";

        upload("/v1/ingest/tasks", "tasks.csv", csv).andExpect(status().isAccepted());
        String first = Files.readString(TASKS_DIR.resolve("tasks.json"));
        upload("/v1/ingest/tasks", "tasks.csv", csv).andExpect(status().isAccepted());
        String second = Files.readString(TASKS_DIR.resolve("tasks.json"));

        assertThat(first).isEqualTo(
                "[{\"Task_ID\":\"t1\",\"Time_To_Complete\":2.5,\"Deadline\":10.0,\"Task_String\":\"Label, then \\\"review\\\"\",\"Category\":\"Legal\"},"
                        + "{\"Task_ID\":\"t2\",\"Time_To_Complete\":0.0,\"Deadline\":1000.0,\"Task_String\":\"second\",\"Category\":\"Legal\"}]");
        assertThat(second).isEqualTo(first);
    }

    @Test
    void headerOnlyFileIngestsZeroRows() throws Exception {
        upload("/v1/ingest/tasks", "tasks.csv", TASKS_HEADER)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.rowsIngested").value(0));

        assertThat(Files.readString(TASKS_DIR.resolve("tasks.json"))).isEqualTo("[]");
    }

    // ---- LLM classification ----

    @Test
    void transientLlmErrorIsRetriedAndRowIsKept() throws Exception {
        when(llmClient.complete(contains("flaky")))
                .thenThrow(new TransientLlmException("503"))
                .thenThrow(new TransientLlmException("timeout"))
                .thenReturn("Geography");

        upload("/v1/ingest/tasks", "tasks.csv", TASKS_HEADER + "t1,1,2,flaky question\n")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.rowsIngested").value(1))
                .andExpect(jsonPath("$.rowsDropped").value(0));

        verify(llmClient, times(3)).complete(contains("flaky"));
        verify(sleeper, times(2)).sleep(any());
        JsonNode json = jsonMapper.readTree(TASKS_DIR.resolve("tasks.json").toFile());
        assertThat(json.get(0).get("Category").asString()).isEqualTo("Geography");
    }

    @Test
    void rowIsDroppedAfterRetriesAreExhaustedAndOthersAreKept() throws Exception {
        when(llmClient.complete(contains("always-down"))).thenThrow(new TransientLlmException("503"));

        upload("/v1/ingest/tasks", "tasks.csv", TASKS_HEADER + "t1,1,2,first\nt2,1,2,always-down\nt3,1,2,third\n")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.rowsIngested").value(2))
                .andExpect(jsonPath("$.rowsDropped").value(1));

        verify(llmClient, times(4)).complete(contains("always-down"));
        JsonNode json = jsonMapper.readTree(TASKS_DIR.resolve("tasks.json").toFile());
        assertThat(json.size()).isEqualTo(2);
        assertThat(json.get(0).get("Task_ID").asString()).isEqualTo("t1");
        assertThat(json.get(1).get("Task_ID").asString()).isEqualTo("t3");
    }

    @Test
    void labelOutsideAllowedListDropsUserRow() throws Exception {
        when(llmClient.complete(contains("Astronaut"))).thenReturn("Astronaut");

        upload("/v1/ingest/users", "users.csv", USERS_HEADER + "u1,Space school,Astronaut,1\nu2,e,x,1\n")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.rowsIngested").value(1))
                .andExpect(jsonPath("$.rowsDropped").value(1));

        JsonNode json = jsonMapper.readTree(USERS_DIR.resolve("users.json").toFile());
        assertThat(json.get(0).get("User_ID").asString()).isEqualTo("u2");
    }

    @Test
    void allRowsDroppedStillWritesEmptyArray() throws Exception {
        when(llmClient.complete(anyString())).thenThrow(new TransientLlmException("503"));

        upload("/v1/ingest/tasks", "tasks.csv", TASKS_HEADER + "t1,1,2,a\n")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.rowsIngested").value(0))
                .andExpect(jsonPath("$.rowsDropped").value(1));

        assertThat(Files.readString(TASKS_DIR.resolve("tasks.json"))).isEqualTo("[]");
    }

    @Test
    void llmRejectionPropagatesItsStatusAndWritesNoFile() throws Exception {
        when(llmClient.complete(anyString())).thenThrow(new LlmRejectedException(401, null));

        upload("/v1/ingest/users", "users.csv", USERS_HEADER + "u1,e,x,1\nu2,e,x,1\n")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("LLM request rejected with status 401"));

        verify(llmClient, times(1)).complete(anyString());
        assertThat(listNames(USERS_DIR)).isEmpty();
    }

    @Test
    void invalidCsvFailsBeforeAnyLlmCall() throws Exception {
        upload("/v1/ingest/tasks", "tasks.csv", "Task_ID,Deadline\nt1,1\n").andExpect(status().isUnprocessableContent());

        verify(llmClient, never()).complete(anyString());
    }

    // ---- 413 ----

    @Test
    void fileOverFiveMegabytesIsRejectedWith413() throws Exception {
        byte[] big = new byte[5 * 1024 * 1024 + 1];

        mockMvc.perform(multipart("/v1/ingest/users").file(new MockMultipartFile("file", "users.csv", "text/csv", big)))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.detail").exists())
                .andExpect(jsonPath("$.trace").doesNotExist());

        assertThat(listNames(USERS_DIR)).isEmpty();
    }

    // ---- 400 ----

    @Test
    void missingFilePartIsBadRequest() throws Exception {
        mockMvc.perform(multipart("/v1/ingest/users")
                        .file(new MockMultipartFile("other", "users.csv", "text/csv", USERS_HEADER.getBytes())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void emptyFileIsBadRequest() throws Exception {
        upload("/v1/ingest/tasks", "tasks.csv", "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Uploaded file is empty"));
    }

    @Test
    void fileWithoutNameIsBadRequest() throws Exception {
        upload("/v1/ingest/users", "", USERS_HEADER + "u1,e,x,1\n").andExpect(status().isBadRequest());
    }

    @Test
    void nonMultipartRequestIsClientError() throws Exception {
        mockMvc.perform(post("/v1/ingest/users").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnsupportedMediaType());
    }

    // ---- 422 ----

    @Test
    void malformedCsvIs422() throws Exception {
        upload("/v1/ingest/users", "users.csv", USERS_HEADER + "u1,\"unterminated,x,1\n")
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void wrongColumnCountIs422() throws Exception {
        upload("/v1/ingest/users", "users.csv", USERS_HEADER + "u1,edu,exp\n")
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void missingRequiredColumnIs422() throws Exception {
        upload("/v1/ingest/tasks", "tasks.csv", "Task_ID,Deadline,Task_String\nt1,1,do it\n")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Time_To_Complete")));
    }

    @Test
    void blankRequiredValueIs422() throws Exception {
        upload("/v1/ingest/users", "users.csv", USERS_HEADER + "u1,,exp,1\n")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("Row 1: Education is required"));
    }

    @Test
    void nonNumericTimeIs422() throws Exception {
        upload("/v1/ingest/tasks", "tasks.csv", TASKS_HEADER + "t1,abc,1,do it\n")
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void negativeOrNonFiniteNumberIs422() throws Exception {
        upload("/v1/ingest/users", "users.csv", USERS_HEADER + "u1,e,x,-1\n").andExpect(status().isUnprocessableContent());
        upload("/v1/ingest/users", "users.csv", USERS_HEADER + "u1,e,x,NaN\n").andExpect(status().isUnprocessableContent());
        upload("/v1/ingest/users", "users.csv", USERS_HEADER + "u1,e,x,1e999\n").andExpect(status().isUnprocessableContent());
    }

    @Test
    void duplicateIdIs422() throws Exception {
        upload("/v1/ingest/tasks", "tasks.csv", TASKS_HEADER + "t1,1,2,a\nt1,1,2,b\n")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("duplicate Task_ID")));
    }

    @Test
    void badRowAfterGoodRowsFailsWholeRequestAndKeepsPreviousOutput() throws Exception {
        upload("/v1/ingest/users", "users.csv", USERS_HEADER + "old,e,x,1\n").andExpect(status().isAccepted());
        String previous = Files.readString(USERS_DIR.resolve("users.json"));

        upload("/v1/ingest/users", "users.csv", USERS_HEADER + "u1,e,x,1\nu2,e,x,1\nu3,e,x,oops\n")
                .andExpect(status().isUnprocessableContent());

        assertThat(Files.readString(USERS_DIR.resolve("users.json"))).isEqualTo(previous);
        assertThat(listNames(USERS_DIR)).containsExactly("users.json");
    }

    private ResultActions upload(String path, String fileName, String csv) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", fileName, "text/csv", csv.getBytes(StandardCharsets.UTF_8));
        return mockMvc.perform(multipart(path).file(file));
    }

    private static List<String> listNames(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files.map(p -> p.getFileName().toString()).toList();
        }
    }

    private static void deleteContents(Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) {
                if (!p.equals(root)) {
                    Files.delete(p);
                }
            }
        }
    }

    private static Path createRoot() {
        try {
            Path root = Files.createTempDirectory("ingest-test");
            root.toFile().deleteOnExit();
            return root;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
