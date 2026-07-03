# CLAUDE.md

Behavioral guidelines to reduce common LLM coding mistakes. Merge with project-specific instructions as needed.

**Tradeoff:** These guidelines bias toward caution over speed. For trivial tasks, use judgment.

---

## 1. Think Before Coding

**Don't assume. Don't hide confusion. Surface tradeoffs.**

Before implementing:
- State your assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them - don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.

## 2. Simplicity First

**Minimum code that solves the problem. Nothing speculative.**

- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If you write 200 lines and it could be 50, rewrite it.

Ask yourself: "Would a senior engineer say this is overcomplicated?" If yes, simplify.

## 3. Surgical Changes

**Touch only what you must. Clean up only your own mess.**

When editing existing code:
- Don't "improve" adjacent code, comments, or formatting.
- Don't refactor things that aren't broken.
- Match existing style, even if you'd do it differently.
- If you notice unrelated dead code, mention it - don't delete it.

When your changes create orphans:
- Remove imports/variables/functions that YOUR changes made unused.
- Don't remove pre-existing dead code unless asked.

The test: Every changed line should trace directly to the user's request.

## 4. Goal-Driven Execution

**Define success criteria. Loop until verified.**

Transform tasks into verifiable goals:
- "Add validation" → "Write tests for invalid inputs, then make them pass"
- "Fix the bug" → "Write a test that reproduces it, then make it pass"
- "Refactor X" → "Ensure tests pass before and after"

For multi-step tasks, state a brief plan:
```
1. [Step] → verify: [check]
2. [Step] → verify: [check]
3. [Step] → verify: [check]
```

Strong success criteria let you loop independently. Weak criteria ("make it work") require constant clarification.

---

**These guidelines are working if:** fewer unnecessary changes in diffs, fewer rewrites due to overcomplication, and clarifying questions come before implementation rather than after mistakes.

---

## 项目上下文：AI 面试助手

### 技术栈
- Java 17+ / Spring Boot 3.3.x / Spring AI Alibaba / MyBatis-Plus / Redis 7.0 / MySQL 8.0
- 前端：单 HTML 通过 CDN 引入 Vue3 + Tailwind CSS + marked.js
- 构建：Maven

### 项目结构
```
src/main/java/com/example/aiinterview/
├── Application.java
├── common/          # Result<T> 统一响应体
├── config/          # 配置类（限流、线程池、CORS、启动自检等）
├── controller/      # InterviewController（SSE流式端点）
├── entity/          # 4张表Entity
├── mapper/          # MyBatis-Plus Mapper
├── service/         # 业务服务层
└── util/            # PdfUtil
```

### 核心架构约束
1. **状态机驱动**：面试阶段流转完全由后端 Redis 状态机控制，LLM 仅负责内容生成
2. **三明治 Prompt**：元指令层 → 上下文层 → 输出约束层，分层注入防护
3. **旁路意图检测**：非面试问题本地规则拦截，不消耗 LLM Token
4. **双路记忆**：短期 Redis List 滑动窗口(10条) + 长期 DB 画像持久化
5. **安全红线**：API Key 仅环境变量、日志不打印 Prompt/JD/简历、启动自检阻断

### 关键配置
- DashScope API Key：环境变量 `DASHSCOPE_API_KEY`
- 会话 TTL：2 小时（Redis）
- DB 保留：30 天
- LLM 重试：指数退避最多 2 次（429/502/503）

### 常用命令
```bash
# 编译
mvn compile

# 启动
mvn spring-boot:run

# 数据库初始化
mysql -u root -p < src/main/resources/schema.sql

# 启动基础设施
docker-compose up -d mysql redis
```
