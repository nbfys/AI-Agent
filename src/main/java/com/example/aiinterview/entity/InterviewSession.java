package com.example.aiinterview.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("interview_session")
public class InterviewSession {

    private Long id;

    private String sessionId;

    private String jdContent;

    private String resumeContent;

    private String status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private LocalDateTime expireTime;

    private String conversationHistory;
}
