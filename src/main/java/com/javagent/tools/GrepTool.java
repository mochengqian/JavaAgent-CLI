package com.javagent.tools;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

/**
 * 文本搜索工具 —— 在文件中搜索匹配的文本
 *
 * 功能：在指定目录下递归搜索文件内容，支持正则表达式
 *
 * 安全机制：
 * - requiresApproval=false：搜索不需要审批
 * - readOnly=true：只读操作
 * - 文件大小限制：跳过超过 256KB 的文件
 * - 二进制文件检测：自动跳过二进制文件
 * - 结果数量限制：最多返回 100 个匹配
 */
public class GrepTool implements Tool {
    private static final long MAX_SIZE_BYTES = 256 * 1024L;
    private static final int MAX_FILES = 200;
    private static final int MAX_MATCHES = 100;

    private static final ToolDefinition DEFINITION = new ToolDefinition(
            "grep",
            "Search text files recursively with a regex pattern.",
            Map.of(
                    "pattern", "Regex pattern to search for.",
                    "path", "File or directory path to search. Defaults to current directory.",
                    "caseSensitive", "Whether the regex match should be case-sensitive."
            ),
            Map.of(
                    "pattern", "string",
                    "path", "string",
                    "caseSensitive", "boolean"
            ),
            Set.of("pattern"),
            false,
            true,
            false,
            List.of("search", "find", "search_text")
    );

    @Override
    public ToolDefinition definition() {
        return DEFINITION;
    }

    @Override
    public ToolExecutionResult execute(Map<String, Object> input) {
        String rawPattern = FileToolSupport.stringValue(input.get("pattern"));
        if (rawPattern.isBlank()) {
            return ToolExecutionResult.error("grep requires a non-empty pattern.");
        }

        String rawPath = FileToolSupport.stringValue(input.get("path"));
        if (rawPath.isBlank()) {
            rawPath = ".";
        }

        Path path;
        try {
            path = FileToolSupport.normalizePath(rawPath);
        } catch (InvalidPathException e) {
            return ToolExecutionResult.error("Invalid path: " + rawPath);
        }

        String wsError = FileToolSupport.checkInsideWorkspace(path);
        if (wsError != null) {
            return ToolExecutionResult.error(wsError);
        }

        if (!Files.exists(path)) {
            return ToolExecutionResult.error("Path not found: " + path);
        }

        boolean caseSensitive = FileToolSupport.booleanValue(input.get("caseSensitive"), false);
        Pattern pattern;
        try {
            pattern = Pattern.compile(rawPattern, caseSensitive ? 0 : Pattern.CASE_INSENSITIVE);
        } catch (PatternSyntaxException e) {
            return ToolExecutionResult.error("Invalid regex pattern: " + e.getMessage());
        }

        StringBuilder builder = new StringBuilder();
        builder.append("Pattern: ").append(rawPattern).append(System.lineSeparator());
        builder.append("Path: ").append(path.toAbsolutePath()).append(System.lineSeparator());
        builder.append("-----").append(System.lineSeparator());

        AtomicInteger filesScanned = new AtomicInteger();
        AtomicInteger matchesFound = new AtomicInteger();

        try {
            if (Files.isRegularFile(path)) {
                searchFile(path, pattern, builder, filesScanned, matchesFound);
            } else {
                try (Stream<Path> stream = Files.walk(path)) {
                    List<Path> files = stream
                            .filter(Files::isRegularFile)
                            .filter(file -> !shouldSkipPath(file))
                            .limit(MAX_FILES)
                            .toList();
                    for (Path file : files) {
                        if (matchesFound.get() >= MAX_MATCHES) {
                            break;
                        }
                        searchFile(file, pattern, builder, filesScanned, matchesFound);
                    }
                }
            }
        } catch (IOException e) {
            return ToolExecutionResult.error("Search failed: " + e.getMessage());
        }

        builder.append("-----").append(System.lineSeparator())
                .append("files_scanned=").append(filesScanned.get())
                .append(", matches=").append(matchesFound.get());
        if (matchesFound.get() >= MAX_MATCHES) {
            builder.append(" (stopped at max matches)");
        }
        return ToolExecutionResult.success(builder.toString().trim());
    }

    private void searchFile(
            Path file,
            Pattern pattern,
            StringBuilder builder,
            AtomicInteger filesScanned,
            AtomicInteger matchesFound
    ) throws IOException {
        if (shouldSkipPath(file) || Files.size(file) > MAX_SIZE_BYTES || FileToolSupport.isBinary(file)) {
            return;
        }
        filesScanned.incrementAndGet();
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        for (int i = 0; i < lines.size(); i++) {
            if (matchesFound.get() >= MAX_MATCHES) {
                return;
            }
            String line = lines.get(i);
            if (pattern.matcher(line).find()) {
                matchesFound.incrementAndGet();
                builder.append(file.toAbsolutePath())
                        .append(":")
                        .append(i + 1)
                        .append(": ")
                        .append(line)
                        .append(System.lineSeparator());
            }
        }
    }

    private boolean shouldSkipPath(Path path) {
        String normalized = path.normalize().toString();
        for (Path part : path.normalize()) {
            String name = part.toString();
            if (name.equals("target") || name.equals(".git") || name.equals(".javaagent-cli")) {
                return true;
            }
        }
        return normalized.endsWith("last_session.json");
    }
}
