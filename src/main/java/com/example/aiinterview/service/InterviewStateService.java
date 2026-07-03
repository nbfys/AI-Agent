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

    // 初始化面试状态
    public void initState(String sessionId) {
        String key = "interview:state:" + sessionId;
        Map<String, Object> state = new HashMap<>();
        state.put("stage", "OPENING");
        state.put("stageTurnCount", 0);
        state.put("totalTurns", 0);

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
}