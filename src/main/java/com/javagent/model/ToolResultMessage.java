package com.javagent.model;

/**
 * 工具执行结果消息 —— 工具执行完成后，封装到 Message 中的结果
 *
 * 当一个工具（如 read_file）执行完毕后，需要把结果告诉 AI，
 * ToolResultMessage 就是承载这个结果的数据结构：
 * - toolCallId：对应的工具调用 ID（与 ToolCall.id 配对）
 * - toolName：工具名称（如 "read_file"）
 * - error：是否执行出错
 * - content：执行结果文本（成功时是结果内容，失败时是错误信息）
 *
 * 示例：
 *   ToolResultMessage.success("call-1", "read_file", "Hello World")
 *   → 工具成功执行，返回了 "Hello World"
 *
 *   ToolResultMessage.error("call-2", "grep", "Pattern not found")
 *   → 工具执行失败，错误信息是 "Pattern not found"
 */
public record ToolResultMessage(
        String toolCallId,   // 对应的 ToolCall ID
        String toolName,     // 工具名称
        boolean error,       // 是否出错
        String content       // 结果内容或错误信息
) {
    /**
     * 创建成功的结果消息
     *
     * @param toolCallId 工具调用 ID
     * @param toolName   工具名称
     * @param content    成功返回的内容
     * @return 成功的 ToolResultMessage
     */
    public static ToolResultMessage success(String toolCallId, String toolName, String content) {
        return new ToolResultMessage(toolCallId, toolName, false, content);
    }

    /**
     * 创建失败的结果消息
     *
     * @param toolCallId 工具调用 ID
     * @param toolName   工具名称
     * @param content    错误信息
     * @return 失败的 ToolResultMessage
     */
    public static ToolResultMessage error(String toolCallId, String toolName, String content) {
        return new ToolResultMessage(toolCallId, toolName, true, content);
    }
}
