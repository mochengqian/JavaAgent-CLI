package com.javagent.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 工具执行统计 —— 追踪每个工具的调用次数、总耗时、错误次数
 */
public class ToolStats {
    private final ConcurrentHashMap<String, StatsEntry> stats = new ConcurrentHashMap<>();

    /** 记录一次工具执行 */
    public void record(String toolName, long durationMs, boolean error) {
        stats.computeIfAbsent(toolName, k -> new StatsEntry())
                .record(durationMs, error);
    }

    /** 获取所有统计信息 */
    public Map<String, StatsEntry> all() {
        return new LinkedHashMap<>(stats);
    }

    /** 重置所有统计 */
    public void clear() {
        stats.clear();
    }

    /** 格式化输出 */
    public String format() {
        if (stats.isEmpty()) {
            return "暂无工具执行记录。";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-18s %6s %8s %6s %8s%n", "工具", "调用", "总耗时", "错误", "平均耗时"));
        sb.append("─".repeat(56)).append("\n");
        for (var entry : stats.entrySet()) {
            String name = entry.getKey();
            StatsEntry s = entry.getValue();
            sb.append(String.format("%-18s %6d %6dms %6d %6dms%n",
                    name, s.calls(), s.totalMs(), s.errors(), s.avgMs()));
        }
        return sb.toString();
    }

    public static class StatsEntry {
        private final AtomicLong calls = new AtomicLong();
        private final AtomicLong totalMs = new AtomicLong();
        private final AtomicLong errors = new AtomicLong();

        void record(long durationMs, boolean error) {
            calls.incrementAndGet();
            totalMs.addAndGet(durationMs);
            if (error) errors.incrementAndGet();
        }

        public long calls() { return calls.get(); }
        public long totalMs() { return totalMs.get(); }
        public long errors() { return errors.get(); }
        public long avgMs() { return calls.get() == 0 ? 0 : totalMs.get() / calls.get(); }
    }
}
