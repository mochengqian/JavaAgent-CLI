package com.javagent.tools;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 读文件工具 —— 读取文本文件的内容
 *
 * 功能：读取指定路径的文本文件，支持 offset（起始行）和 limit（行数限制）
 *
 * 安全机制：
 * - requiresApproval=false：读文件不需要审批
 * - readOnly=true：只读操作，不会修改文件
 * - 文件大小限制：超过 256KB 拒绝读取
 * - 二进制文件检测：自动跳过二进制文件
 */
public class ReadFileTool implements Tool {
    private static final int DEFAULT_LIMIT = 200;
    private static final int MAX_LIMIT = 1000;
    private static final long MAX_SIZE_BYTES = 256 * 1024L;

    private static final ToolDefinition DEFINITION = new ToolDefinition(
            "read_file",
            "Read a text file with optional offset and line limit.",
            Map.of(
                    "path", "The file path to read.",
                    "offset", "Zero-based starting line offset.",
                    "limit", "Maximum number of lines to return."
            ),
            Map.of(
                    "path", "string",
                    "offset", "integer",
                    "limit", "integer"
            ),
            Set.of("path"),
            false,
            true,
            false,
            List.of("read", "cat", "open_file")
    );

    @Override
    public ToolDefinition definition() {
        return DEFINITION;
    }

    @Override
    public ToolExecutionResult execute(Map<String, Object> input) {
        String rawPath = FileToolSupport.stringValue(input.get("path"));
        if (rawPath.isBlank()) {
            return ToolExecutionResult.error("read_file requires a non-empty path.");
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
            return ToolExecutionResult.error("File not found: " + path);
        }
        if (!Files.isRegularFile(path)) {
            return ToolExecutionResult.error("Path is not a regular file: " + path);
        }

        try {
            long size = Files.size(path);
            if (size > MAX_SIZE_BYTES) {
                return ToolExecutionResult.error("File is too large to read safely (" + size + " bytes).");
            }
            if (FileToolSupport.isBinary(path)) {
                return ToolExecutionResult.error("Binary files are not supported by read_file.");
            }

            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            int offset = Math.max(0, FileToolSupport.intValue(input.get("offset"), 0));
            int limit = Math.min(MAX_LIMIT, Math.max(1, FileToolSupport.intValue(input.get("limit"), DEFAULT_LIMIT)));
            int start = Math.min(offset, lines.size());
            int end = Math.min(start + limit, lines.size());

            StringBuilder builder = new StringBuilder();
            builder.append("File: ").append(path.toAbsolutePath()).append(System.lineSeparator());
            builder.append("Lines: ").append(start + 1).append("-").append(end).append(" of ").append(lines.size()).append(System.lineSeparator());
            builder.append("-----").append(System.lineSeparator());
            for (int i = start; i < end; i++) {
                builder.append(i + 1).append(": ").append(lines.get(i)).append(System.lineSeparator());
            }
            if (end < lines.size()) {
                builder.append("... (").append(lines.size() - end).append(" more lines omitted)");
            }

            return ToolExecutionResult.success(builder.toString().trim());
        } catch (IOException e) {
            return ToolExecutionResult.error("Failed to read file: " + e.getMessage());
        }
    }
}
