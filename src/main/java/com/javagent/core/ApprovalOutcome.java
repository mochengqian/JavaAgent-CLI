package com.javagent.core;

/**
 * 审批结果 —— 一次审批的最终决定
 *
 * 包含三个信息：
 * - approved：是否批准执行
 * - fromCache：是否来自缓存（之前审批过，这次自动沿用）
 * - reason：审批理由（如"只读工具自动通过"、"用户确认执行"）
 *
 * 什么是"审批缓存"？
 * 如果用户已经批准了"读取 README.md"，下次再读同一个文件时，
 * 不需要再问用户，直接沿用上次的审批结果，这叫缓存。
 * 这样可以减少重复确认，提升使用体验。
 */
public record ApprovalOutcome(
        boolean approved,   // 是否批准
        boolean fromCache,  // 是否来自缓存
        String reason       // 审批理由
) {
    /** 创建"批准"结果（非缓存） */
    public static ApprovalOutcome approved(String reason) {
        return new ApprovalOutcome(true, false, reason);
    }

    /** 创建"批准"结果（来自缓存） */
    public static ApprovalOutcome cachedApproved(String reason) {
        return new ApprovalOutcome(true, true, reason);
    }

    /** 创建"拒绝"结果（非缓存） */
    public static ApprovalOutcome denied(String reason) {
        return new ApprovalOutcome(false, false, reason);
    }

    /** 创建"拒绝"结果（来自缓存） */
    public static ApprovalOutcome cachedDenied(String reason) {
        return new ApprovalOutcome(false, true, reason);
    }
}
