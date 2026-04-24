package com.example.aiinterview.config;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class RateLimiter {
    // 令牌桶容量
    private static final int CAPACITY = 10;
    // 每秒补充的令牌数
    private static final int REFILL_RATE = 2;
    
    private final AtomicInteger tokens = new AtomicInteger(CAPACITY);
    private final AtomicLong lastRefillTime = new AtomicLong(System.currentTimeMillis());
    
    /**
     * 获取令牌
     * @return 是否成功获取到令牌
     */
    public boolean tryAcquire() {
        refill();
        int current = tokens.get();
        if (current > 0) {
            return tokens.compareAndSet(current, current - 1);
        }
        return false;
    }
    
    /**
     * 补充令牌
     */
    private void refill() {
        long now = System.currentTimeMillis();
        long last = lastRefillTime.get();
        long elapsed = now - last;
        
        if (elapsed >= 1000) { // 每秒补充一次
            int tokensToAdd = (int) (elapsed / 1000) * REFILL_RATE;
            int current = tokens.get();
            int newTokens = Math.min(CAPACITY, current + tokensToAdd);
            
            if (tokens.compareAndSet(current, newTokens)) {
                lastRefillTime.set(now);
            }
        }
    }
    
    /**
     * 获取当前可用令牌数
     */
    public int getAvailableTokens() {
        refill();
        return tokens.get();
    }
}
