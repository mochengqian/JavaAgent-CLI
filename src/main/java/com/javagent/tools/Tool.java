package com.javagent.tools;

import java.nio.file.Path;
import java.util.Map;

/**
 * 工具接口 —— 所有工具的统一规范
 *
 * 什么是"工具"？
 * AI 模型本身只能生成文本，不能直接操作文件系统或执行命令。
 * "工具"就是给 AI 伸出的"手"，让它能真正做事：
 * - 读取文件（read_file）
 * - 搜索文本（grep）
 * - 写入文件（write_file）
 * - 删除文件（delete_file）
 * - 列出目录（list_directory）
 * - 执行命令（bash）
 *
 * 每个工具只需要实现两个方法：
 * 1. definition() → 告诉系统这个工具的元信息（名称、参数、是否需要审批等）
 * 2. execute()     → 实际执行操作，返回结果
 *
 * 这种设计叫"策略模式"——统一接口，不同实现。
 */
public interface Tool {
    /**
     * 获取工具的元信息定义
     *
     * @return 工具定义（名称、描述、参数、是否需要审批等）
     */
    ToolDefinition definition();

    /**
     * 执行工具操作
     *
     * @param input 工具参数（如 {"path": "test.txt", "limit": 50}）
     * @return 执行结果（成功内容或错误信息）
     */
    ToolExecutionResult execute(Map<String, Object> input);

    /**
     * 配置工具的工作区根目录（可选）
     * 文件工具应覆盖此方法以启用路径安全检查
     */
    default void configure(java.nio.file.Path workspaceRoot) {
        // 默认无操作
    }
}
