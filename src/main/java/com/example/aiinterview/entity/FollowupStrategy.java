package com.example.aiinterview.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("followup_strategy")
public class FollowupStrategy {

    private Long id;

    private String keyword;

    private String dimension;

    private String followupQuestion;

    private Integer minScoreTrigger;

    private Integer sortOrder;

    private LocalDateTime createTime;
}
