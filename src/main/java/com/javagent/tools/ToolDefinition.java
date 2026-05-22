package com.javagent.tools;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 工具定义 —— 描述一个工具的元信息
 *
 * 就像商品标签一样，ToolDefinition 告诉系统：
 * - 这个工具叫什么名字？
 * - 它是做什么的？
 * - 需要什么参数？
 * - 参数是什么类型？
 * - 哪些参数是必填的？
 * - 是否需要用户审批？
 * - 是只读的还是可修改的？
 * - 是破坏性的吗？
 * - 有没有别名？
 *
 * 这些信息有两个用途：
 * 1. 发送给 AI 模型，让它知道有哪些工具可用
 * 2. 审批系统用来判断是否需要用户确认
 *
 * 为什么用 record？
 * 因为工具定义是固定的元数据，创建后不需要修改。
 * record 自动生成 getter 和不可变性保证。
 */
public record ToolDefinition(
        String name,                             // 工具名称（如 "read_file"）
        String description,                      // 工具描述
        Map<String, String> parameterDescriptions, // 参数描述（如 {"path": "文件路径"}）
        Map<String, String> parameterTypes,       // 参数类型（如 {"path": "string"}）
        Set<String> requiredParameters,           // 必填参数名集合
        boolean requiresApproval,                 // 是否需要用户审批
        boolean readOnly,                         // 是否只读（不修改文件系统）
        boolean destructive,                      // 是否破坏性操作（如删除）
        List<String> aliases                      // 工具别名（如 ["read", "cat"]）
) {
    /**
     * 紧凑构造器 —— 对 null 值做安全处理，转为不可变集合
     * 防止外部修改影响内部数据
     */
    public ToolDefinition {
        parameterDescriptions = parameterDescriptions == null
                ? Map.of()
                : Map.copyOf(new LinkedHashMap<>(parameterDescriptions));
        parameterTypes = parameterTypes == null
                ? Map.of()
                : Map.copyOf(new LinkedHashMap<>(parameterTypes));
        requiredParameters = requiredParameters == null
                ? Set.of()
                : Set.copyOf(new LinkedHashSet<>(requiredParameters));
        aliases = aliases == null ? List.of() : List.copyOf(aliases);
    }
}
