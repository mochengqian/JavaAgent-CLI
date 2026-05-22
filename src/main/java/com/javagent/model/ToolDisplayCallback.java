package com.javagent.model;

/**
 * Callback for displaying tool execution progress in the CLI.
 * The Agent calls these methods so the UI can show colored spinners,
 * tool names, and result summaries.
 */
public interface ToolDisplayCallback {
    /** Called when a tool call begins execution */
    void onToolStart(String toolName, String summary);

    /** Called when a tool call completes */
    void onToolEnd(String toolName, boolean success, String resultSummary);

    /** Called when a tool call completes, with full result content for rich display */
    default void onToolEnd(String toolName, boolean success, String resultSummary, String fullContent) {
        onToolEnd(toolName, success, resultSummary);
    }
}
