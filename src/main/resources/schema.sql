-- ============================================================
-- AI面试助手 数据库初始化脚本
-- 合规依据：PRD v2.0 数据库合规 第8条 - 4张表完整实现
-- ============================================================

CREATE DATABASE IF NOT EXISTS `interview_db` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `interview_db`;

-- 1. 面试会话表
DROP TABLE IF EXISTS `interview_session`;
CREATE TABLE `interview_session` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `session_id` VARCHAR(64) NOT NULL COMMENT '会话唯一标识',
    `jd_content` TEXT COMMENT '岗位描述',
    `resume_content` TEXT COMMENT '简历内容',
    `status` VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS' COMMENT '会话状态: IN_PROGRESS/COMPLETED/EXPIRED',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `expire_time` DATETIME COMMENT '过期时间(创建时间+30天)',
    `conversation_history` LONGTEXT COMMENT '对话历史(JSON序列化)',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_session_id` (`session_id`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='面试会话表';

-- 2. 题库表
DROP TABLE IF EXISTS `question_bank`;
CREATE TABLE `question_bank` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `keyword` VARCHAR(100) NOT NULL COMMENT '关键词',
    `standard_answer` TEXT COMMENT '标准答案/参考要点',
    `category` VARCHAR(50) DEFAULT 'GENERAL' COMMENT '分类: GENERAL/JAVA/SPRING/DB/AI/BEHAVIOR',
    `difficulty` TINYINT DEFAULT 3 COMMENT '难度 1-5',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_keyword` (`keyword`),
    KEY `idx_category` (`category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='题库表';

-- 3. 追问策略表
DROP TABLE IF EXISTS `followup_strategy`;
CREATE TABLE `followup_strategy` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `keyword` VARCHAR(100) NOT NULL COMMENT '触发关键词',
    `dimension` VARCHAR(50) NOT NULL COMMENT '追问维度',
    `followup_question` TEXT NOT NULL COMMENT '追问问题模板',
    `min_score_trigger` TINYINT DEFAULT 5 COMMENT '最低评分触发追问',
    `sort_order` INT DEFAULT 0 COMMENT '排序',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_keyword` (`keyword`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='追问策略表';

-- 4. 候选人画像表
DROP TABLE IF EXISTS `candidate_profile`;
CREATE TABLE `candidate_profile` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `session_id` VARCHAR(64) NOT NULL COMMENT '会话ID',
    `technical_stack` TEXT COMMENT '技术栈',
    `key_projects` TEXT COMMENT '关键项目',
    `strengths` TEXT COMMENT '优势',
    `weaknesses` TEXT COMMENT '薄弱点',
    `career_goals` TEXT COMMENT '职业目标',
    `pain_points` TEXT COMMENT '痛点',
    `work_experience` TEXT COMMENT '工作经验',
    `education` TEXT COMMENT '教育背景',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_session_id` (`session_id`),
    KEY `idx_update_time` (`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='候选人画像表';

-- 初始化题库样例数据
INSERT INTO `question_bank` (`keyword`, `standard_answer`, `category`, `difficulty`) VALUES
('自我介绍', '简洁介绍教育背景、工作经历、技术栈、项目亮点', 'GENERAL', 1),
('项目经验', 'STAR原则：背景、任务、行动、结果，突出量化指标', 'GENERAL', 3),
('Spring IOC', '控制反转：将对象创建和管理交给容器，降低耦合', 'JAVA', 4),
('Redis缓存', '缓存穿透/击穿/雪崩的定义和解决方案', 'DB', 4);
