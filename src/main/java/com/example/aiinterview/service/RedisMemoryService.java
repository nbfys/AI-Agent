package com.example.aiinterview.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    // 添加已问问题（存储标准化后的字符串，便于去重比较）
    public void addAskedQuestion(String sessionId, String question) {
        String key = "interview:asked:" + sessionId;
        // 直接存储原始问题，但比较时会标准化
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

    // 缓存JD内容
    public void cacheJdContent(String sessionId, String jdContent) {
        String key = "interview:jd:" + sessionId;
        redisTemplate.opsForValue().set(key, jdContent);
        redisTemplate.expire(key, TTL, TimeUnit.SECONDS);
    }

    // 获取JD内容
    public String getJdContent(String sessionId) {
        String key = "interview:jd:" + sessionId;
        Object value = redisTemplate.opsForValue().get(key);
        return value != null ? value.toString() : null;
    }

    // 缓存简历内容
    public void cacheResumeContent(String sessionId, String resumeContent) {
        String key = "interview:resume:" + sessionId;
        redisTemplate.opsForValue().set(key, resumeContent);
        redisTemplate.expire(key, TTL, TimeUnit.SECONDS);
    }

    // 获取简历内容
    public String getResumeContent(String sessionId) {
        String key = "interview:resume:" + sessionId;
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
        redisTemplate.delete("interview:profile:" + sessionId);
        redisTemplate.delete("interview:jd:" + sessionId);
        redisTemplate.delete("interview:resume:" + sessionId);
    }

    // 保存候选人画像
    public void saveCandidateProfile(String sessionId, MetadataExtractionService.CandidateProfile profile) {
        String key = "interview:profile:" + sessionId;
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            String profileJson = objectMapper.writeValueAsString(profile);
            redisTemplate.opsForValue().set(key, profileJson);
            redisTemplate.expire(key, TTL, TimeUnit.SECONDS);
        } catch (Exception e) {
            System.err.println("[RedisMemoryService] 保存候选人画像失败：" + e.getMessage());
        }
    }

    // 获取候选人画像
    public MetadataExtractionService.CandidateProfile getCandidateProfile(String sessionId) {
        String key = "interview:profile:" + sessionId;
        Object value = redisTemplate.opsForValue().get(key);
        if (value != null) {
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                return objectMapper.readValue(value.toString(), MetadataExtractionService.CandidateProfile.class);
            } catch (Exception e) {
                System.err.println("[RedisMemoryService] 获取候选人画像失败：" + e.getMessage());
            }
        }
        return null;
    }
}
