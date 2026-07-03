package com.example.aiinterview.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.aiinterview.entity.InterviewSession;
import com.example.aiinterview.mapper.InterviewSessionMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class InterviewSessionService {

    @Resource
    private InterviewSessionMapper interviewSessionMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public void insert(InterviewSession session) {
        interviewSessionMapper.insert(session);
    }

    public InterviewSession selectBySessionId(String sessionId) {
        LambdaQueryWrapper<InterviewSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InterviewSession::getSessionId, sessionId);
        return interviewSessionMapper.selectOne(wrapper);
    }

    public void updateBySessionId(String sessionId, String status, String conversationHistory) {
        InterviewSession session = selectBySessionId(sessionId);
        if (session != null) {
            session.setStatus(status);
            session.setUpdateTime(LocalDateTime.now());
            if (conversationHistory != null) {
                session.setConversationHistory(conversationHistory);
            }
            interviewSessionMapper.updateById(session);
        }
    }

    @Async
    public void asyncSaveHistory(String sessionId, List<String> history) {
        try {
            String historyJson = objectMapper.writeValueAsString(history);
            InterviewSession session = selectBySessionId(sessionId);
            if (session != null) {
                session.setConversationHistory(historyJson);
                session.setUpdateTime(LocalDateTime.now());
                interviewSessionMapper.updateById(session);
            }
        } catch (JsonProcessingException e) {
            // 静默失败，不影响主流程
        }
    }
}
