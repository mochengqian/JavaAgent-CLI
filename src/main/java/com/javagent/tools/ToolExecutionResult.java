package com.javagent.tools;

/**
 * 工具执行结果 —— 工具执行后的返回值
 *
 * 每个工具执行后，无论成功还是失败，都返回一个 ToolExecutionResult：
 * - content：结果文本（成功时是输出内容，失败时是错误描述）
 * - error：是否出错
 *
 * 示例：
 *   ToolExecutionResult.success("Hello World")    → 成功，内容是 "Hello World"
 *   ToolExecutionResult.error("文件不存在")         → 失败，错误信息是 "文件不存在"
 */
public record ToolExecutionResult(
        String content,   // 结果文本
        boolean error     // 是否出错
) {
    /** 紧凑构造器 —— content 为 null 时设为空字符串 */
    public ToolExecutionResult {
        content = content == null ? "" : content;
    }

    /** 创建成功结果 */
    public static ToolExecutionResult success(String content) {
        return new ToolExecutionResult(content, false);
    }

    /** 创建失败结果 */
    public static ToolExecutionResult error(String content) {
        return new ToolExecutionResult(content, true);
    }
}
