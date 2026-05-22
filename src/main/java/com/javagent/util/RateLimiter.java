package com.javagent.util;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 令牌桶速率限制器 —— 防止高频 API 调用
 *
 * 原理：桶以固定速率填充令牌，每次请求消耗一个令牌。
 * 桶空时请求被阻塞等待，直到有新令牌可用。
 */
public final class RateLimiter {
    private final long nanosPerPermit;  // 每个令牌的间隔时间（纳秒）
    private final long maxTokens;       // 桶容量
    private final AtomicLong tokens;    // 当前可用令牌数
    private volatile long lastRefillNanos; // 上次填充时间

    /**
     * @param maxPerSecond 每秒最大请求数（QPS 上限）
     */
    public RateLimiter(long maxPerSecond) {
        this(maxPerSecond, maxPerSecond);
    }

    /**
     * @param maxPerSecond 每秒最大请求数
     * @param burstSize    突发容量（允许的瞬间并发数）
     */
    public RateLimiter(long maxPerSecond, long burstSize) {
        this.nanosPerPermit = 1_000_000_000L / maxPerSecond;
        this.maxTokens = burstSize;
        this.tokens = new AtomicLong(burstSize);
        this.lastRefillNanos = System.nanoTime();
    }

    /**
     * 获取一个令牌，必要时阻塞等待
     */
    public void acquire() throws InterruptedException {
        while (true) {
            refill();
            long current = tokens.get();
            if (current > 0 && tokens.compareAndSet(current, current - 1)) {
                return;
            }
            // 等待一个令牌间隔
            Thread.sleep(Math.max(1, nanosPerPermit / 1_000_000));
        }
    }

    /**
     * 尝试获取令牌，不阻塞
     * @return true 如果获取成功
     */
    public boolean tryAcquire() {
        refill();
        long current = tokens.get();
        return current > 0 && tokens.compareAndSet(current, current - 1);
    }

    private void refill() {
        long now = System.nanoTime();
        long elapsed = now - lastRefillNanos;
        if (elapsed < nanosPerPermit) {
            return;
        }
        long newTokens = elapsed / nanosPerPermit;
        if (newTokens > 0) {
            long refillTime = lastRefillNanos + newTokens * nanosPerPermit;
            lastRefillNanos = refillTime;
            long current = tokens.get();
            long updated = Math.min(maxTokens, current + newTokens);
            tokens.compareAndSet(current, updated);
        }
    }
}
