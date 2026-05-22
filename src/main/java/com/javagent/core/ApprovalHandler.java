package com.javagent.core;

import com.javagent.model.ToolCall;

/**
 * 审批处理器接口 —— 向用户请求审批的回调
 *
 * 为什么用接口？
 * 因为"向用户请求审批"这个动作，在 CLI 和 GUI 中实现方式不同：
 * - CLI 版本：在终端打印提示，等用户输入 yes/no
 * - GUI 版本：弹出对话框
 *
 * 通过接口，Agent 不需要知道具体怎么问用户，
 * 只需要调用 approvalHandler.request(toolCall) 就行。
 *
 * 这就是"依赖倒置"——高层模块不依赖低层模块，都依赖抽象。
 */
public interface ApprovalHandler {
    /**
     * 请求用户审批
     *
     * @param toolCall 需要审批的工具调用
     * @return 用户的审批决策
     */
    ApprovalDecision request(ToolCall toolCall);
}
