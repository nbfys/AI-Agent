package com.example.aiinterview.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "interview")
public class InterviewConfig {
    private int maxTotalTurns = 20;
    private int maxStageTurns = 4;
    private long sseTimeoutMs = 300000L;
    private int sseCorePoolSize = 10;
    private int sseMaxPoolSize = 50;
    private int sseQueueCapacity = 100;
    private int minTurnsBeforeEnd = 15;
    private int maxRegenerateRetries = 2;

    public int getMaxTotalTurns() {
        return maxTotalTurns;
    }

    public void setMaxTotalTurns(int maxTotalTurns) {
        this.maxTotalTurns = maxTotalTurns;
    }

    public int getMaxStageTurns() {
        return maxStageTurns;
    }

    public void setMaxStageTurns(int maxStageTurns) {
        this.maxStageTurns = maxStageTurns;
    }

    public long getSseTimeoutMs() {
        return sseTimeoutMs;
    }

    public void setSseTimeoutMs(long sseTimeoutMs) {
        this.sseTimeoutMs = sseTimeoutMs;
    }

    public int getSseCorePoolSize() {
        return sseCorePoolSize;
    }

    public void setSseCorePoolSize(int sseCorePoolSize) {
        this.sseCorePoolSize = sseCorePoolSize;
    }

    public int getSseMaxPoolSize() {
        return sseMaxPoolSize;
    }

    public void setSseMaxPoolSize(int sseMaxPoolSize) {
        this.sseMaxPoolSize = sseMaxPoolSize;
    }

    public int getSseQueueCapacity() {
        return sseQueueCapacity;
    }

    public void setSseQueueCapacity(int sseQueueCapacity) {
        this.sseQueueCapacity = sseQueueCapacity;
    }

    public int getMinTurnsBeforeEnd() {
        return minTurnsBeforeEnd;
    }

    public void setMinTurnsBeforeEnd(int minTurnsBeforeEnd) {
        this.minTurnsBeforeEnd = minTurnsBeforeEnd;
    }

    public int getMaxRegenerateRetries() {
        return maxRegenerateRetries;
    }

    public void setMaxRegenerateRetries(int maxRegenerateRetries) {
        this.maxRegenerateRetries = maxRegenerateRetries;
    }
}
