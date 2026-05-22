package com.javagent.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 工具调用请求 —— AI 决定调用某个工具时创建的对象
 *
 * 当 AI 认为需要使用工具时，会返回一个 ToolCall，包含：
 * - id：本次调用的唯一标识（用于把工具结果和调用配对）
 * - name：要调用的工具名称（如 "read_file"、"grep"）
 * - input：传给工具的参数（如 {"path": "README.md", "limit": 50}）
 *
 * 什么是 record？
 * Java 16+ 引入的特殊类，自动生成构造器、getter、equals、hashCode、toString。
 * 适合用来定义不可变的数据容器。
 *
 * 示例：
 *   ToolCall call = ToolCall.of("read_file", Map.of("path", "test.txt"));
 *   call.name()   → "read_file"
 *   call.input()  → {path=test.txt}
 */
public record ToolCall(
        String id,                    // 调用唯一 ID
        String name,                  // 工具名称
        Map<String, Object> input     // 工具参数
) {
    /**
     * 紧凑构造器 —— 在对象创建时做数据校验和默认值填充
     * 如果 id 为空，自动生成一个 UUID
     * 如果 input 为空，用空 Map 代替（避免 NullPointerException）
     */
    public ToolCall {
        id = (id == null || id.isBlank()) ? UUID.randomUUID().toString() : id;
        input = input == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(input));
    }

    /**
     * 工厂方法 —— 快速创建一个 ToolCall（自动生成 id）
     *
     * @param name  工具名称
     * @param input 工具参数
     * @return 新的 ToolCall 实例
     */
    public static ToolCall of(String name, Map<String, Object> input) {
        return new ToolCall(UUID.randomUUID().toString(), name, input);
    }
}
