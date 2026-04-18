package com.example.aiinterview.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class InterviewSession {

    private Long id;

    private String sessionId;

    private String jdContent;

    private String resumeContent;

    private LocalDateTime createTime;
}
