package com.javagent.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 对话消息 —— 整个系统中最核心的数据结构
 *
 * 每一轮对话都会产生一条 Message，它包含：
 * - id：消息唯一标识
 * - role：谁说的（系统/用户/AI/工具）
 * - content：消息文本内容
 * - timestamp：消息时间
 * - toolCalls：AI 请求调用的工具列表（仅 ASSISTANT 角色有）
 * - toolResult：工具执行结果（仅 TOOL 角色有）
 *
 * 一条消息不会同时有 toolCalls 和 toolResult：
 * - 用户消息：只有 content
 * - AI 文字回复：只有 content
 * - AI 工具调用：有 content + toolCalls
 * - 工具结果：有 content + toolResult
 *
 * 示例：
 *   Message.user("读取 README.md")                          → 用户消息
 *   Message.assistant("我来帮你看一下")                       → AI 文字回复
 *   Message.assistantWithToolCalls("...", toolCallList)      → AI 请求工具
 *   Message.toolResult("id-1", "read_file", "内容...", false) → 工具结果
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Message(
        String id,                       // 消息唯一 ID
        Role role,                       // 消息角色
        String content,                  // 消息文本
        LocalDateTime timestamp,         // 消息时间
        List<ToolCall> toolCalls,        // AI 请求的工具调用（ASSISTANT 时可能有）
        ToolResultMessage toolResult,    // 工具执行结果（TOOL 时有）
        String reasoningContent          // thinking 模型的推理内容（需回传）
) {
    /**
     * 紧凑构造器 —— 创建 Message 时自动填充默认值
     * - id 为空时自动生成 UUID
     * - content 为空时设为 ""
     * - timestamp 为空时使用当前时间
     * - toolCalls 为空时设为空列表（不可变）
     */
    public Message {
        id = (id == null || id.isBlank()) ? UUID.randomUUID().toString() : id;
        content = content == null ? "" : content;
        timestamp = timestamp == null ? LocalDateTime.now() : timestamp;
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    /** 创建系统消息 —— 用于设置 AI 行为 */
    public static Message system(String content) {
        return new Message(null, Role.SYSTEM, content, null, List.of(), null, null);
    }

    /** 创建用户消息 —— 用户的输入 */
    public static Message user(String content) {
        return new Message(null, Role.USER, content, null, List.of(), null, null);
    }

    /** 创建 AI 纯文本回复消息 */
    public static Message assistant(String content) {
        return new Message(null, Role.ASSISTANT, content, null, List.of(), null, null);
    }

    /** 创建 AI 纯文本回复消息（含 thinking 推理内容） */
    public static Message assistant(String content, String reasoningContent) {
        return new Message(null, Role.ASSISTANT, content, null, List.of(), null, reasoningContent);
    }

    /** 创建 AI 工具调用消息 —— AI 决定使用工具时 */
    public static Message assistantWithToolCalls(String content, List<ToolCall> toolCalls) {
        return new Message(null, Role.ASSISTANT, content, null, toolCalls, null, null);
    }

    /** 创建 AI 工具调用消息（含 thinking 推理内容） */
    public static Message assistantWithToolCalls(String content, List<ToolCall> toolCalls, String reasoningContent) {
        return new Message(null, Role.ASSISTANT, content, null, toolCalls, null, reasoningContent);
    }

    /**
     * 创建工具结果消息 —— 工具执行完毕后
     *
     * @param toolCallId 对应的 ToolCall ID
     * @param toolName   工具名称
     * @param content    结果内容
     * @param error      是否出错
     */
    public static Message toolResult(String toolCallId, String toolName, String content, boolean error) {
        ToolResultMessage result = error
                ? ToolResultMessage.error(toolCallId, toolName, content)
                : ToolResultMessage.success(toolCallId, toolName, content);
        return new Message(null, Role.TOOL, content, null, List.of(), result, null);
    }

    /** 是否包含工具调用请求 */
    @JsonIgnore
    public boolean hasToolCalls() {
        return !toolCalls.isEmpty();
    }

    /** 工具执行是否出错 */
    @JsonIgnore
    public boolean isToolError() {
        return toolResult != null && toolResult.error();
    }
}
