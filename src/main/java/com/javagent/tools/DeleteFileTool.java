package com.javagent.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 删除文件工具 —— 删除指定路径的普通文件
 *
 * 功能：删除一个文件（不支持删除目录）
 *
 * 安全机制：
 * - requiresApproval=true：需要用户确认才能执行
 * - destructive=true：标记为破坏性操作
 * - 只能删除普通文件，不能删除目录
 */
public class DeleteFileTool implements Tool {
    private static final ToolDefinition DEFINITION = new ToolDefinition(
            "delete_file",
            "Delete a regular file. Directories are not supported.",
            Map.of("path", "File path to delete."),
            Map.of("path", "string"),
            Set.of("path"),
            true,
            false,
            true,
            List.of("delete", "remove", "rm_file")
    );

    @Override
    public ToolDefinition definition() {
        return DEFINITION;
    }

    @Override
    public ToolExecutionResult execute(Map<String, Object> input) {
        String rawPath = FileToolSupport.stringValue(input.get("path"));
        if (rawPath.isBlank()) {
            return ToolExecutionResult.error("delete_file requires a non-empty path.");
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
            return ToolExecutionResult.error("delete_file only supports regular files: " + path);
        }

        try {
            Files.delete(path);
            return ToolExecutionResult.success("Deleted file: " + path.toAbsolutePath());
        } catch (IOException e) {
            return ToolExecutionResult.error("Failed to delete file: " + e.getMessage());
        }
    }
}
