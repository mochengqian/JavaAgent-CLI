package com.javagent.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javagent.core.Config;
import com.javagent.tools.ToolDefinition;
import com.javagent.util.RateLimiter;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * OpenAI-compatible model client — calls real AI APIs with SSE streaming support.
 *
 * Supports any OpenAI API-compatible service (OpenAI, DeepSeek, Moonshot, etc.).
 * When a TextStreamHandler is provided, uses true SSE streaming for token-by-token output.
 */
public class OpenAiCompatibleModelClient implements ModelClient {
    private static final Logger LOG = Logger.getLogger(OpenAiCompatibleModelClient.class.getName());
    private static final int MAX_RETRIES = 3;
    private static final long BASE_DELAY_MS = 1000;
    private final Config config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RateLimiter rateLimiter;

    public OpenAiCompatibleModelClient(Config config) {
        this.config = config;
        this.rateLimiter = new RateLimiter(config.rateLimitQps());
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    @Override
    public ModelResponse chat(String systemPrompt, List<Message> messages, List<ToolDefinition> tools) {
        if (config.apiKey().isBlank()) {
            return ModelResponse.error("Real mode requires an API key. Configure agent.api_key or switch back to mock mode.");
        }

        try {
            // Try with tools first
            String body = buildRequestBody(systemPrompt, messages, tools, false);
            HttpRequest request = buildRequest(body);
            HttpResponse<String> response = sendWithRetry(request);
            if (response.statusCode() >= 400) {
                // If 400 and tools were provided, retry without tools (model may not support function calling)
                if (response.statusCode() == 400 && !tools.isEmpty()) {
                    String bodyNoTools = buildRequestBody(systemPrompt, messages, List.of(), false);
                    HttpRequest retryRequest = buildRequest(bodyNoTools);
                    HttpResponse<String> retryResponse = sendWithRetry(retryRequest);
                    if (retryResponse.statusCode() < 400) {
                        return parseResponse(retryResponse.body());
                    }
                }
                return ModelResponse.error("HTTP " + response.statusCode() + ": " + truncate(response.body(), 500));
            }
            return parseResponse(response.body());
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            return ModelResponse.error("Request failed: " + e.getMessage());
        }
    }

    @Override
    public ModelResponse chat(String systemPrompt, List<Message> messages,
                               List<ToolDefinition> tools, TextStreamHandler streamHandler) {
        // No streaming requested or no handler: fall back to non-streaming
        if (streamHandler == null) {
            return chat(systemPrompt, messages, tools);
        }
        // Mock mode: use simulated streaming
        if (config.isMockMode()) {
            return chat(systemPrompt, messages, tools);
        }

        if (config.apiKey().isBlank()) {
            return ModelResponse.error("Real mode requires an API key.");
        }

        try {
            String body = buildRequestBody(systemPrompt, messages, tools, true);
            HttpRequest request = buildRequest(body);

            // Use SSE streaming via BodyHandlers.ofLines()
            StringBuilder contentBuilder = new StringBuilder();
            StringBuilder reasoningBuilder = new StringBuilder();
            List<ToolCallDelta> toolCallDeltas = new ArrayList<>();

            HttpResponse<java.util.stream.Stream<String>> response = httpClient.send(request, HttpResponse.BodyHandlers.ofLines());
            if (response.statusCode() >= 400) {
                // Close the stream
                response.body().close();
                // If 400 with tools, fall back to non-streaming which handles retry
                if (response.statusCode() == 400 && !tools.isEmpty()) {
                    return chat(systemPrompt, messages, tools);
                }
                return ModelResponse.error("HTTP " + response.statusCode());
            }

            response.body().forEach(line -> {
                if (line.isEmpty() || line.startsWith(":")) return; // skip comments/keepalives
                if (!line.startsWith("data: ")) return;

                String data = line.substring(6).trim();
                if ("[DONE]".equals(data)) return;

                try {
                    JsonNode chunk = objectMapper.readTree(data);
                    JsonNode choices = chunk.path("choices");
                    if (choices.isEmpty()) return;

                    JsonNode delta = choices.path(0).path("delta");

                    // Content delta
                    JsonNode contentNode = delta.path("content");
                    if (!contentNode.isMissingNode() && !contentNode.isNull()) {
                        String text = contentNode.asText("");
                        if (!text.isEmpty()) {
                            contentBuilder.append(text);
                            streamHandler.onChunk(text);
                        }
                    }

                    // Reasoning content delta (thinking models)
                    JsonNode reasoningNode = delta.path("reasoning_content");
                    if (!reasoningNode.isMissingNode() && !reasoningNode.isNull()) {
                        String reasoning = reasoningNode.asText("");
                        if (!reasoning.isEmpty()) {
                            reasoningBuilder.append(reasoning);
                        }
                    }

                    // Tool call deltas
                    JsonNode toolCallsNode = delta.path("tool_calls");
                    if (toolCallsNode.isArray()) {
                        for (JsonNode tc : toolCallsNode) {
                            int index = tc.path("index").asInt(0);
                            // Ensure list is large enough
                            while (toolCallDeltas.size() <= index) {
                                toolCallDeltas.add(new ToolCallDelta());
                            }
                            ToolCallDelta tcd = toolCallDeltas.get(index);
                            if (tc.has("id") && !tc.path("id").asText("").isEmpty()) {
                                tcd.id = tc.path("id").asText();
                            }
                            JsonNode fn = tc.path("function");
                            if (fn.has("name") && !fn.path("name").asText("").isEmpty()) {
                                tcd.name = fn.path("name").asText();
                            }
                            if (fn.has("arguments")) {
                                tcd.arguments.append(fn.path("arguments").asText(""));
                            }
                        }
                    }

                } catch (JsonProcessingException e) {
                    LOG.log(Level.FINE, "Skipped malformed SSE chunk", e);
                }
            });

            // Build response from accumulated data
            String reasoning = reasoningBuilder.isEmpty() ? null : reasoningBuilder.toString();
            if (!toolCallDeltas.isEmpty() && toolCallDeltas.stream().anyMatch(t -> t.name != null)) {
                List<ToolCall> toolCalls = toolCallDeltas.stream()
                        .filter(t -> t.id != null && t.name != null)
                        .map(t -> {
                            Map<String, Object> args = parseArguments(t.arguments.toString());
                            return new ToolCall(t.id, t.name, args);
                        })
                        .toList();
                return ModelResponse.toolCalls(contentBuilder.toString(), toolCalls, reasoning);
            }

            String content = contentBuilder.toString();
            if (content.isEmpty() && reasoning != null) {
                // Some thinking models return only reasoning_content with no content
                content = reasoning;
            }
            if (content.isEmpty()) {
                return ModelResponse.error("Empty response from API.");
            }
            return ModelResponse.text(content, reasoning);

        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            String msg = e.getMessage();
            return ModelResponse.error("Streaming request failed: " + (msg != null ? msg : e.getClass().getSimpleName()));
        }
    }

    @Override
    public String name() {
        return "openai-compatible";
    }

    private HttpRequest buildRequest(String body) {
        return HttpRequest.newBuilder()
                .uri(URI.create(config.baseUrl() + "/chat/completions"))
                .timeout(Duration.ofSeconds(120))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + config.apiKey())
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    /**
     * 带指数退避的 HTTP 请求重试（处理 429 和 5xx）
     */
    private HttpResponse<String> sendWithRetry(HttpRequest request) throws IOException, InterruptedException {
        rateLimiter.acquire();
        IOException lastException = null;
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();
                if (status == 429 || status >= 500) {
                    if (attempt < MAX_RETRIES) {
                        long delay = BASE_DELAY_MS * (1L << attempt); // 1s, 2s, 4s
                        LOG.log(Level.WARNING, "HTTP " + status + ", retrying in " + delay + "ms (attempt " + (attempt + 1) + "/" + MAX_RETRIES + ")");
                        Thread.sleep(delay);
                        continue;
                    }
                }
                return response;
            } catch (IOException e) {
                lastException = e;
                if (attempt < MAX_RETRIES) {
                    long delay = BASE_DELAY_MS * (1L << attempt);
                    LOG.log(Level.WARNING, "IO error, retrying in " + delay + "ms (attempt " + (attempt + 1) + "/" + MAX_RETRIES + "): " + e.getMessage());
                    Thread.sleep(delay);
                }
            }
        }
        throw lastException;
    }

    private String buildRequestBody(String systemPrompt, List<Message> messages,
                                      List<ToolDefinition> tools, boolean stream) throws JsonProcessingException {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", config.model());
        root.put("temperature", 0.0);
        root.put("tool_choice", "auto");
        if (stream) {
            root.put("stream", true);
            // Enable stream options for usage info
            ObjectNode streamOptions = objectMapper.createObjectNode();
            streamOptions.put("include_usage", false);
            root.set("stream_options", streamOptions);
        }

        ArrayNode messagesNode = objectMapper.createArrayNode();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            ObjectNode systemMessage = objectMapper.createObjectNode();
            systemMessage.put("role", "system");
            systemMessage.put("content", systemPrompt);
            messagesNode.add(systemMessage);
        }

        for (Message message : messages) {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("role", message.role().apiValue());

            switch (message.role()) {
                case USER, SYSTEM -> node.put("content", message.content());
                case ASSISTANT -> {
                    node.put("content", message.content());
                    // Include reasoning_content for thinking models (must be passed back)
                    if (message.reasoningContent() != null && !message.reasoningContent().isEmpty()) {
                        node.put("reasoning_content", message.reasoningContent());
                    }
                    if (message.hasToolCalls()) {
                        ArrayNode toolCallsNode = objectMapper.createArrayNode();
                        for (ToolCall toolCall : message.toolCalls()) {
                            ObjectNode toolCallNode = objectMapper.createObjectNode();
                            toolCallNode.put("id", toolCall.id());
                            toolCallNode.put("type", "function");
                            ObjectNode functionNode = objectMapper.createObjectNode();
                            functionNode.put("name", toolCall.name());
                            functionNode.put("arguments", objectMapper.writeValueAsString(toolCall.input()));
                            toolCallNode.set("function", functionNode);
                            toolCallsNode.add(toolCallNode);
                        }
                        node.set("tool_calls", toolCallsNode);
                    }
                }
                case TOOL -> {
                    if (message.toolResult() == null) {
                        node.put("content", message.content());
                    } else {
                        node.put("tool_call_id", message.toolResult().toolCallId());
                        node.put("content", message.toolResult().content());
                    }
                }
            }
            messagesNode.add(node);
        }
        root.set("messages", messagesNode);

        ArrayNode toolsNode = objectMapper.createArrayNode();
        for (ToolDefinition tool : tools) {
            ObjectNode toolNode = objectMapper.createObjectNode();
            toolNode.put("type", "function");
            ObjectNode functionNode = objectMapper.createObjectNode();
            functionNode.put("name", tool.name());
            functionNode.put("description", tool.description());
            ObjectNode parametersNode = objectMapper.createObjectNode();
            parametersNode.put("type", "object");
            ObjectNode propertiesNode = objectMapper.createObjectNode();
            tool.parameterDescriptions().forEach((name, description) -> {
                ObjectNode propertyNode = objectMapper.createObjectNode();
                propertyNode.put("type", tool.parameterTypes().getOrDefault(name, "string"));
                propertyNode.put("description", description);
                propertiesNode.set(name, propertyNode);
            });
            parametersNode.set("properties", propertiesNode);
            ArrayNode requiredNode = objectMapper.createArrayNode();
            for (String name : tool.requiredParameters()) {
                requiredNode.add(name);
            }
            parametersNode.set("required", requiredNode);
            functionNode.set("parameters", parametersNode);
            toolNode.set("function", functionNode);
            toolsNode.add(toolNode);
        }
        root.set("tools", toolsNode);

        return objectMapper.writeValueAsString(root);
    }

    private ModelResponse parseResponse(String responseBody) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(responseBody);
        if (root.has("error")) {
            return ModelResponse.error(root.path("error").path("message").asText("Unknown API error"));
        }

        JsonNode messageNode = root.path("choices").path(0).path("message");
        if (messageNode.isMissingNode()) {
            return ModelResponse.error("Response did not contain a message.");
        }

        // Extract reasoning_content for thinking models
        String reasoningContent = null;
        JsonNode reasoningNode = messageNode.path("reasoning_content");
        if (!reasoningNode.isMissingNode() && !reasoningNode.isNull()) {
            reasoningContent = reasoningNode.isTextual() ? reasoningNode.asText() : reasoningNode.toString();
        }

        JsonNode toolCallsNode = messageNode.path("tool_calls");
        if (toolCallsNode.isArray() && !toolCallsNode.isEmpty()) {
            List<ToolCall> toolCalls = objectMapper.convertValue(toolCallsNode, new TypeReference<List<JsonNode>>() {})
                    .stream()
                    .map(node -> {
                        String id = node.path("id").asText();
                        String name = node.path("function").path("name").asText();
                        String rawArguments = node.path("function").path("arguments").asText("{}");
                        Map<String, Object> input = parseArguments(rawArguments);
                        return new ToolCall(id, name, input);
                    })
                    .toList();
            return ModelResponse.toolCalls(extractContent(messageNode.path("content")), toolCalls, reasoningContent);
        }

        return ModelResponse.text(extractContent(messageNode.path("content")), reasoningContent);
    }

    private Map<String, Object> parseArguments(String rawArguments) {
        try {
            return objectMapper.readValue(rawArguments, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (JsonProcessingException e) {
            return Map.of("raw", rawArguments);
        }
    }

    private String extractContent(JsonNode contentNode) {
        if (contentNode == null || contentNode.isMissingNode() || contentNode.isNull()) {
            return "";
        }
        if (contentNode.isTextual()) {
            return contentNode.asText();
        }
        if (contentNode.isArray()) {
            StringBuilder builder = new StringBuilder();
            for (JsonNode block : contentNode) {
                if (block.has("text")) {
                    builder.append(block.path("text").asText());
                }
            }
            return builder.toString();
        }
        return contentNode.toString();
    }

    /** Accumulator for streaming tool call deltas */
    private static class ToolCallDelta {
        String id;
        String name;
        final StringBuilder arguments = new StringBuilder();
    }
}
