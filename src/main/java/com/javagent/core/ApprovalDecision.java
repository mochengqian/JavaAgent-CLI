package com.javagent.core;

/**
 * 审批决策枚举 —— 用户对工具调用的审批结果
 *
 * 当 AI 要执行一个需要审批的操作（如写文件、执行命令）时，
 * 系统会暂停，让用户确认。用户有三种选择：
 *
 * - APPROVED：同意执行
 * - DENIED：拒绝执行
 * - CANCELLED：取消（不表态，直接中止）
 */
public enum ApprovalDecision {
    APPROVED,    // 用户同意
    DENIED,      // 用户拒绝
    CANCELLED;   // 用户取消

    /** 是否是"同意" */
    public boolean isApproved() {
        return this == APPROVED;
    }
}
