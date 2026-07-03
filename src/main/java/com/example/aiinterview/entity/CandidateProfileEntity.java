package com.example.aiinterview.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("candidate_profile")
public class CandidateProfileEntity {

    private Long id;

    private String sessionId;

    private String technicalStack;

    private String keyProjects;

    private String strengths;

    private String weaknesses;

    private String careerGoals;

    private String painPoints;

    private String workExperience;

    private String education;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
