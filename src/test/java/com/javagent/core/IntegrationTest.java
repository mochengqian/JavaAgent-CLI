package com.javagent.core;

import com.javagent.model.Message;
import com.javagent.model.ModelClient;
import com.javagent.model.ModelResponse;
import com.javagent.model.ToolCall;
import com.javagent.tools.EditTool;
import com.javagent.tools.ToolDefinition;
import com.javagent.tools.GrepTool;
import com.javagent.tools.ReadFileTool;
import com.javagent.tools.ToolRegistry;
import com.javagent.tools.WriteFileTool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 端到端集成测试 —— 验证 Agent + ToolRegistry + 真实文件系统的完整工具链
 */
class IntegrationTest {
    @TempDir
    Path tempDir;

    @Test
    void readEditWriteChain() throws IOException {
        // Setup: create a source file
        Path source = tempDir.resolve("src.txt");
        Files.writeString(source, "hello world\nfoo bar\nbaz qux\n");

        Path dest = tempDir.resolve("dest.txt");

        Config config = Config.load(tempDir);
        ConversationManager manager = new ConversationManager(config);
        ToolRegistry registry = new ToolRegistry();
        registry.setWorkspaceRoot(tempDir);
        registry.register(new ReadFileTool());
        registry.register(new EditTool());
        registry.register(new WriteFileTool());
        registry.register(new GrepTool());

        // Model sequence: read → edit → write → final text
        var client = new SequenceModelClient(List.of(
                // Step 1: Read source file
                ModelResponse.toolCalls("Reading source file", List.of(
                        ToolCall.of("read_file", Map.of("path", source.toString()))
                )),
                // Step 2: Edit source file
                ModelResponse.toolCalls("Editing source file", List.of(
                        ToolCall.of("edit", Map.of(
                                "path", source.toString(),
                                "old_string", "foo bar",
                                "new_string", "FOO BAR"
                        ))
                )),
                // Step 3: Write result to dest
                ModelResponse.toolCalls("Writing to dest", List.of(
                        ToolCall.of("write_file", Map.of(
                                "path", dest.toString(),
                                "content", "edited content from src.txt"
                        ))
                )),
                // Step 4: Final text
                ModelResponse.text("Done: read, edited, and wrote the file.")
        ));

        Agent agent = new Agent(config, client, registry, manager);

        String response = agent.processTurn("process the file", tc -> ApprovalDecision.APPROVED);

        assertTrue(response.contains("Done"));
        assertTrue(Files.readString(dest).contains("edited content"));
        assertTrue(Files.readString(source).contains("FOO BAR"));
        assertFalse(Files.readString(source).contains("foo bar"));
        assertTrue(manager.messageCount() >= 8); // user + 3*(assistant+tool) + final
    }

    @Test
    void grepThenReadChain() throws IOException {
        // Setup: multiple files
        Path file1 = tempDir.resolve("a.txt");
        Path file2 = tempDir.resolve("b.txt");
        Files.writeString(file1, "alpha beta gamma\n");
        Files.writeString(file2, "delta epsilon zeta\n");

        Config config = Config.load(tempDir);
        ConversationManager manager = new ConversationManager(config);
        ToolRegistry registry = new ToolRegistry();
        registry.setWorkspaceRoot(tempDir);
        registry.register(new GrepTool());
        registry.register(new ReadFileTool());

        var client = new SequenceModelClient(List.of(
                // Step 1: Grep for "alpha"
                ModelResponse.toolCalls("Searching", List.of(
                        ToolCall.of("grep", Map.of("pattern", "alpha", "path", tempDir.toString()))
                )),
                // Step 2: Read the found file
                ModelResponse.toolCalls("Reading match", List.of(
                        ToolCall.of("read_file", Map.of("path", file1.toString()))
                )),
                // Step 3: Final
                ModelResponse.text("Found 'alpha' in a.txt, line 1.")
        ));

        Agent agent = new Agent(config, client, registry, manager);

        String response = agent.processTurn("find alpha", tc -> ApprovalDecision.APPROVED);

        assertTrue(response.contains("alpha"));
        assertTrue(manager.messageCount() >= 6);
    }

    @Test
    void errorRecoveryInChain() throws IOException {
        Config config = Config.load(tempDir);
        ConversationManager manager = new ConversationManager(config);
        ToolRegistry registry = new ToolRegistry();
        registry.setWorkspaceRoot(tempDir);
        registry.register(new ReadFileTool());
        registry.register(new WriteFileTool());

        Path missing = tempDir.resolve("nope.txt");
        Path output = tempDir.resolve("output.txt");

        var client = new SequenceModelClient(List.of(
                // Step 1: Try reading missing file → error
                ModelResponse.toolCalls("Try read", List.of(
                        ToolCall.of("read_file", Map.of("path", missing.toString()))
                )),
                // Step 2: Recover by writing a new file
                ModelResponse.toolCalls("Recover", List.of(
                        ToolCall.of("write_file", Map.of("path", output.toString(), "content", "recovered"))
                )),
                ModelResponse.text("Recovered from error, wrote output file.")
        ));

        Agent agent = new Agent(config, client, registry, manager);

        String response = agent.processTurn("do something", tc -> ApprovalDecision.APPROVED);

        assertTrue(response.contains("Recovered"));
        assertTrue(Files.readString(output).contains("recovered"));
    }

    private static final class SequenceModelClient implements ModelClient {
        private final Deque<ModelResponse> responses;

        SequenceModelClient(List<ModelResponse> responses) {
            this.responses = new ArrayDeque<>(responses);
        }

        @Override
        public ModelResponse chat(String systemPrompt, List<Message> messages, List<ToolDefinition> tools) {
            return responses.isEmpty() ? ModelResponse.text("sequence complete") : responses.removeFirst();
        }

        @Override
        public String name() {
            return "sequence";
        }
    }
}
