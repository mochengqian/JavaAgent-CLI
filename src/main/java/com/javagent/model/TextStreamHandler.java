package com.javagent.model;

/**
 * 流式文本处理器 —— 用于逐块输出 AI 的回复
 *
 * 什么是"流式输出"？
 * 当 AI 生成一段长文本时，不用等全部生成完再显示，
 * 而是一边生成一边显示，就像打字机一样逐字出现。
 * 这种方式用户体验更好，不用长时间等待。
 *
 * 使用方式：
 *   TextStreamHandler handler = chunk -> System.out.print(chunk);
 *   handler.onChunk("你");  // 立刻显示"你"
 *   handler.onChunk("好");  // 立刻显示"好"
 *
 * @FunctionalInterface 表示这是一个函数式接口，可以用 Lambda 表达式创建
 */
@FunctionalInterface
public interface TextStreamHandler {
    /**
     * 收到一块文本时的回调方法
     *
     * @param chunk 本次收到的一小段文本（通常几个字到几十个字）
     */
    void onChunk(String chunk);
}
