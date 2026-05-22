package com.javagent.model;

import java.util.List;

/**
 * 模型响应 —— AI 模型返回的完整响应
 *
 * AI 模型的回复有三种类型：
 * 1. TEXT：纯文本回复（如 "这是一个排序算法..."）
 * 2. TOOL_CALLS：请求调用工具（如 "我需要先读取文件 → read_file"）
 * 3. ERROR：请求出错（如 API Key 无效、网络超时）
 *
 * 为什么用 record？
 * 因为 ModelResponse 是一个纯数据容器，不需要可变状态。
 * record 自动生成 getter、equals、hashCode，代码更简洁。
 *
 * 示例：
 *   ModelResponse.text("Hello!")                    → 纯文本回复
 *   ModelResponse.toolCalls("...", toolCallList)    → 工具调用请求
 *   ModelResponse.error("API key missing")          → 错误响应
 */
public record ModelResponse(
        ResponseType type,         // 响应类型
        String content,            // 文本内容（TEXT 类型时有值）
        List<ToolCall> toolCalls,  // 工具调用列表（TOOL_CALLS 类型时有值）
        String errorMessage,       // 错误信息（ERROR 类型时有值）
        String reasoningContent    // thinking 模型的推理内容（需回传）
) {
    /** 响应类型枚举 —— 模型回复的三种可能 */
    public enum ResponseType {
        TEXT,        // 纯文本回复
        TOOL_CALLS,  // 请求调用工具
        ERROR        // 出错了
    }

    /**
     * 紧凑构造器 —— 对 null 值做安全处理
     * 避免 NullPointerException，给字段设合理默认值
     */
    public ModelResponse {
        content = content == null ? "" : content;
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        errorMessage = errorMessage == null ? "" : errorMessage;
    }

    public static ModelResponse text(String content) {
        return new ModelResponse(ResponseType.TEXT, content, List.of(), "", null);
    }

    public static ModelResponse text(String content, String reasoningContent) {
        return new ModelResponse(ResponseType.TEXT, content, List.of(), "", reasoningContent);
    }

    public static ModelResponse toolCalls(String content, List<ToolCall> toolCalls) {
        return new ModelResponse(ResponseType.TOOL_CALLS, content, toolCalls, "", null);
    }

    public static ModelResponse toolCalls(String content, List<ToolCall> toolCalls, String reasoningContent) {
        return new ModelResponse(ResponseType.TOOL_CALLS, content, toolCalls, "", reasoningContent);
    }

    public static ModelResponse error(String errorMessage) {
        return new ModelResponse(ResponseType.ERROR, "", List.of(), errorMessage, null);
    }

    /** 是否是纯文本回复 */
    public boolean isText() {
        return type == ResponseType.TEXT;
    }

    /** 是否是工具调用请求 */
    public boolean isToolCalls() {
        return type == ResponseType.TOOL_CALLS;
    }

    /** 是否是错误响应 */
    public boolean isError() {
        return type == ResponseType.ERROR;
    }
}
