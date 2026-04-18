# 🔴 AI 面试助手 Agent (Mock Interviewer Pro) - 开发需求白皮书

## 一、 项目背景与架构约束

你是一个资深全栈架构师。你需要为我从零生成一个“AI 面试助手”的全栈 Web 项目。该项目要求极简部署，采用前后端分离，但前端不使用脚手架，直接使用 CDN 引入。

### 1. 技术栈要求（严格遵守）

- **后端开发语言:** Java 17
- **后端框架:** Spring Boot 3.3.x, Spring Web
- **持久层:** MyBatis-Plus, MySQL 8.0
- **缓存与记忆:** Redis (Spring Data Redis)
- **AI 接入框架:** Spring AI Alibaba (接入通义千问 dashscope)
- **前后端通信:** RESTful API + **SSE (Server-Sent Events) 单向长连接**
- **前端技术:** 单个 `index.html` 文件，通过 CDN 引入 Vue 3 (Composition API), Tailwind CSS, marked.js (用于 Markdown 渲染)。
- **构建工具:** Maven

### 2. 全局规范

- 所有后端 API 必须处理跨域 (CORS)，允许 `*` 访问。
- 使用 Lombok 简化 Java 代码。
- 提供统一的 `Result<T>` 响应体（针对非 SSE 接口）和全局异常处理器 `GlobalExceptionHandler`。
- 将 AI 的 System Prompt 抽取在配置文件或常量子类中。

***

## 二、 数据库设计 (MySQL DDL)

请基于以下 SQL 自动生成对应的 Entity、Mapper 和 Service。

```sql
-- 会话表：存储用户简历与JD初始化信息
CREATE TABLE `interview_session` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `session_id` varchar(64) NOT NULL COMMENT '前端UUID',
  `jd_content` text COMMENT '岗位要求',
  `resume_content` text COMMENT '简历内容',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_session_id` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 题库表：用于 Function Calling 防止大模型幻觉
CREATE TABLE `question_bank` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `keyword` varchar(100) NOT NULL COMMENT '触发关键词',
  `standard_answer` text NOT NULL COMMENT '标准答案',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 预置一条测试数据
INSERT INTO `question_bank` (`keyword`, `standard_answer`) VALUES ('Redis雪崩', 'Redis雪崩是指大量缓存同时过期，导致请求直接打到数据库。解决方案：1.随机过期时间；2.加锁排队；3.热点数据永不过期。');
```

***

## 三、 核心后端业务模块与 API 定义

### 1. 会话初始化 API

- **Endpoint:** `POST /api/interview/init`
- **入参:** `MultipartFile resume` (PDF文件), `String jdContent` (岗位要求文本)
- **业务逻辑:**
  1. 使用简单工具（如 PDFBox 或直接当作纯文本读取测试）提取 PDF 文本。
  2. 生成一个全局唯一的 `sessionId` (UUID)。
  3. 将 `sessionId`, `jdContent`, `resumeContent` 保存入 `interview_session` 表。
- **出参:** `{ "code": 200, "data": { "sessionId": "..." }, "msg": "success" }`

### 2. AI 核心对话引擎 (SSE 流式输出)

- **Endpoint:** `GET /api/interview/chat/stream?sessionId={sessionId}&userMessage={userMessage}`
- **返回类型:** `SseEmitter` (`text/event-stream`)
- **业务逻辑 (极度核心):**
  1. **参数判断：** 如果 `userMessage` 为空，说明是第一次连接。AI 需要根据数据库中的 `jdContent` 和 `resumeContent` 生成**开场白与第一道面试题**。
  2. **如果** **`userMessage`** **不为空：** 说明用户正在回答问题。
  3. **短期记忆管理 (Redis)：** 使用 `RedisTemplate` 的 List 结构。每次对话前，拉取该 `sessionId` 最近的 10 条历史消息（滑动窗口截断），连同本次 `userMessage` 一起组装成 Prompt 发给大模型。
  4. **Function Calling (防幻觉工具)：** 向 Spring AI 注册一个 `@Tool` 方法 `searchStandardAnswer(String keyword)`，该方法查询 `question_bank` 表。让大模型在遇到技术问题需要打分时，自动调用此工具。
  5. **流式推送：** 将大模型返回的 `Flux<String>` 逐块通过 `SseEmitter.send()` 推送给前端。
  6. **落盘：** 对话结束后，将完整的 AI 问答追加进 Redis 历史记录列表。

***

## 四、 前端 UI/UX 规格说明 (单文件 HTML)

你需要生成一个包含在项目 `src/main/resources/static/index.html` 中的单页面。

- **样式框架:** Tailwind CSS (CDN 引入)。
- **JS 框架:** Vue 3 (CDN 引入，使用 setup 语法)。
- **UI 布局要求:**
  - **整体:** 类似 ChatGPT 的现代极简暗黑/明亮风格，高度 100vh，Flex 布局。
  - **左侧侧边栏 (w-1/3):**
    - 一个 Textarea，用于粘贴 JD（占位符：“请粘贴目标岗位 JD...”）。
    - 一个 File Input，用于上传 PDF 简历。
    - 一个显眼的 Button：“开始模拟面试”。点击后调用 `/init` 接口，拿到 `sessionId` 后，侧边栏进入不可编辑的“面试中”状态。
  - **右侧主区域 (w-2/3):**
    - 上方为主聊天展示区。使用 `marked.js` 将接收到的文本实时渲染为 Markdown（包含代码块样式）。
    - 需要实现“打字机”平滑滚动的视觉效果。
    - 下方为用户输入区。包含一个 Textarea 和发送按钮。仅在获取到 `sessionId` 后方可使用。
- **交互逻辑:**
  - 拿到 `sessionId` 后，立刻使用原生的 `EventSource` 对象连接 `GET /api/interview/chat/stream`，接收初始面试题。
  - 用户输入回答点击发送后，将内容 append 到聊天界面（靠右侧显示），并再次建立 `EventSource` 连接后端发送请求，接收 AI 的流式回复（靠左侧显示）。

***

## 五、 开发阶段执行计划 (Phases)

**致 AI：请严格按照以下步骤生成，每次完成一个 Phase 后请停止，等待我的确认后再进行下一个 Phase。**

- **Phase 1: 基础设施构建**
  1. 生成标准的 Spring Boot `pom.xml`，包含 Web, MyBatis-Plus, MySQL, Redis, Spring AI Alibaba (dashscope) 依赖。
  2. 生成 `application.yml` 配置模板（MySQL、Redis、DashScope API Key 留占位符）。
  3. 基于 SQL 自动生成 Entity, Mapper, Service 层代码。
  4. 生成统一响应实体 `Result<T>` 和全局跨域配置类 `CorsConfig`。
- **Phase 2: 后端核心业务实现**
  1. 实现 `InterviewController`，包含 `/init` 和 `/chat/stream` 两个接口。
  2. 实现 PDF 文本提取逻辑（简化版即可）。
  3. 实现基于 Redis List 的滑动窗口记忆服务 (`RedisMemoryService`)。
  4. 利用 Spring AI Alibaba ChatClient 实现 SSE 流式调用，并正确注册查题库的 Tool 方法。
- **Phase 3: 前端单页面生成**
  1. 在 `static/index.html` 中生成完整的 Vue3 + Tailwind 前端代码。
  2. 实现 `EventSource` 的流式数据接收解析逻辑。
  3. 确保页面美观且无需任何额外的前端 node 编译步骤即可在浏览器双击运行或由 Tomcat 直接伺服。

