package com.example.aiinterview.service;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class InterviewStateService {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    private static final long TTL = 2 * 60 * 60; // 2小时

    // 面试阶段枚举
    public enum Stage {
        OPENING,        // 开场
        MOTIVATION,     // 动机
        PROJECT,        // 项目
        BACKEND,        // 后端技术
        LLM_RAG,        // LLM 与 RAG
        BEHAVIOR,       // 行为
        CLOSEOUT        // 结束
    }

    // 初始化面试状态
    public void initState(String sessionId) {
        String key = "interview:state:" + sessionId;
        Map<String, Object> state = new HashMap<>();
        state.put("stage", Stage.OPENING.name());
        state.put("stageTurnCount", 0);
        state.put("totalTurns", 0);
        state.put("coverageMap", "{}"); // 初始为空 JSON
        
        redisTemplate.opsForHash().putAll(key, state);
        redisTemplate.expire(key, TTL, TimeUnit.SECONDS);
    }

    // 获取面试状态
    public Map<String, Object> getState(String sessionId) {
        String key = "interview:state:" + sessionId;
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<Object, Object> entry : entries.entrySet()) {
            result.put(entry.getKey().toString(), entry.getValue());
        }
        return result;
    }

    // 更新面试状态
    public void updateState(String sessionId, Map<String, Object> updates) {
        String key = "interview:state:" + sessionId;
        for (Map.Entry<String, Object> entry : updates.entrySet()) {
            redisTemplate.opsForHash().put(key, entry.getKey(), entry.getValue());
        }
        redisTemplate.expire(key, TTL, TimeUnit.SECONDS);
    }

    // 获取当前阶段
    public Stage getCurrentStage(String sessionId) {
        String key = "interview:state:" + sessionId;
        Object stageObj = redisTemplate.opsForHash().get(key, "stage");
        if (stageObj != null) {
            return Stage.valueOf(stageObj.toString());
        }
        return Stage.OPENING;
    }

    // 获取当前阶段轮次
    public int getStageTurnCount(String sessionId) {
        String key = "interview:state:" + sessionId;
        Object countObj = redisTemplate.opsForHash().get(key, "stageTurnCount");
        if (countObj != null) {
            return Integer.parseInt(countObj.toString());
        }
        return 0;
    }

    // 获取总轮次
    public int getTotalTurns(String sessionId) {
        String key = "interview:state:" + sessionId;
        Object turnsObj = redisTemplate.opsForHash().get(key, "totalTurns");
        if (turnsObj != null) {
            return Integer.parseInt(turnsObj.toString());
        }
        return 0;
    }

    // 推进到下一阶段
    public Stage advanceStage(String sessionId) {
        Stage currentStage = getCurrentStage(sessionId);
        Stage nextStage = getNextStage(currentStage);
        
        Map<String, Object> updates = new HashMap<>();
        updates.put("stage", nextStage.name());
        updates.put("stageTurnCount", 0);
        updateState(sessionId, updates);
        
        return nextStage;
    }

    // 获取下一阶段
    private Stage getNextStage(Stage currentStage) {
        switch (currentStage) {
            case OPENING:
                return Stage.MOTIVATION;
            case MOTIVATION:
                return Stage.PROJECT;
            case PROJECT:
                return Stage.BACKEND;
            case BACKEND:
                return Stage.LLM_RAG;
            case LLM_RAG:
                return Stage.BEHAVIOR;
            case BEHAVIOR:
                return Stage.CLOSEOUT;
            case CLOSEOUT:
                return Stage.CLOSEOUT;
            default:
                return Stage.OPENING;
        }
    }

    // 增加轮次计数
    public void incrementTurns(String sessionId) {
        String key = "interview:state:" + sessionId;
        
        // 增加总轮次
        int totalTurns = getTotalTurns(sessionId) + 1;
        redisTemplate.opsForHash().put(key, "totalTurns", totalTurns);
        
        // 增加当前阶段轮次
        int stageTurnCount = getStageTurnCount(sessionId) + 1;
        redisTemplate.opsForHash().put(key, "stageTurnCount", stageTurnCount);
        
        // 检查是否需要推进阶段
        if (stageTurnCount > 3) {
            advanceStage(sessionId);
        }
        
        // 检查是否需要强制结束
        if (totalTurns >= 20) {
            Map<String, Object> updates = new HashMap<>();
            updates.put("stage", Stage.CLOSEOUT.name());
            updateState(sessionId, updates);
        }
        
        redisTemplate.expire(key, TTL, TimeUnit.SECONDS);
    }

    // 更新能力点覆盖情况
    public void updateCoverageMap(String sessionId, String coverageMap) {
        String key = "interview:state:" + sessionId;
        redisTemplate.opsForHash().put(key, "coverageMap", coverageMap);
        redisTemplate.expire(key, TTL, TimeUnit.SECONDS);
    }

    // 检查是否需要结束面试
    public boolean shouldEndInterview(String sessionId) {
        Stage currentStage = getCurrentStage(sessionId);
        int totalTurns = getTotalTurns(sessionId);
        return currentStage == Stage.CLOSEOUT || totalTurns >= 20;
    }
}