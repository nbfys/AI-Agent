package com.example.aiinterview.service;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class RedisMemoryService {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    private static final int MAX_HISTORY_SIZE = 10;
    private static final long TTL = 2 * 60 * 60; // 2小时

    // 添加消息到历史记录
    public void addMessage(String sessionId, String message) {
        String key = "interview:history:" + sessionId;
        
        // 修复：rightPush = 新消息追加到末尾（正序：旧→新）
        redisTemplate.opsForList().rightPush(key, message);
        
        // 修复：保留末尾最新的 MAX_HISTORY_SIZE 条
        Long size = redisTemplate.opsForList().size(key);
        if (size != null && size > MAX_HISTORY_SIZE) {
            redisTemplate.opsForList().trim(key, size - MAX_HISTORY_SIZE, -1);
        }
        
        redisTemplate.expire(key, TTL, TimeUnit.SECONDS);
    }

    // 获取历史记录
    public List<String> getHistory(String sessionId) {
        String key = "interview:history:" + sessionId;
        // 修复：range(0, -1) 取全部，rightPush 后已经是正序（旧→新）
        List<Object> objects = redisTemplate.opsForList().range(key, 0, -1);
        List<String> history = new ArrayList<>();
        if (objects != null) {
            for (Object obj : objects) {
                history.add(obj.toString());
            }
        }
        return history;
    }

    // 添加已问问题
    public void addAskedQuestion(String sessionId, String question) {
        String key = "interview:asked:" + sessionId;
        redisTemplate.opsForSet().add(key, question);
        // 设置过期时间
        redisTemplate.expire(key, TTL, TimeUnit.SECONDS);
    }

    // 获取已问问题
    public Set<Object> getAskedQuestions(String sessionId) {
        String key = "interview:asked:" + sessionId;
        return redisTemplate.opsForSet().members(key);
    }

    // 缓存系统提示
    public void cacheSystemPrompt(String sessionId, String systemPrompt) {
        String key = "interview:system_prompt:" + sessionId;
        redisTemplate.opsForValue().set(key, systemPrompt);
        // 设置过期时间
        redisTemplate.expire(key, TTL, TimeUnit.SECONDS);
    }

    // 获取系统提示
    public String getSystemPrompt(String sessionId) {
        String key = "interview:system_prompt:" + sessionId;
        Object value = redisTemplate.opsForValue().get(key);
        return value != null ? value.toString() : null;
    }

    // 缓存摘要
    public void cacheSummary(String sessionId, String summary) {
        String key = "interview:summary:" + sessionId;
        redisTemplate.opsForValue().set(key, summary);
        // 设置过期时间
        redisTemplate.expire(key, TTL, TimeUnit.SECONDS);
    }

    // 获取摘要
    public String getSummary(String sessionId) {
        String key = "interview:summary:" + sessionId;
        Object value = redisTemplate.opsForValue().get(key);
        return value != null ? value.toString() : null;
    }

    // 清理会话数据
    public void cleanSession(String sessionId) {
        redisTemplate.delete("interview:history:" + sessionId);
        redisTemplate.delete("interview:asked:" + sessionId);
        redisTemplate.delete("interview:system_prompt:" + sessionId);
        redisTemplate.delete("interview:summary:" + sessionId);
        redisTemplate.delete("interview:state:" + sessionId);
    }
}
