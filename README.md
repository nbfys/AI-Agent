# AI 面试助手 Agent

## 项目简介

AI 面试助手 Agent 是一个基于大语言模型的智能面试系统，专为 Java 后端和 AI 工程方向的技术面试设计。它能够模拟资深技术面试官，通过深度技术对话评估候选人水平，提供专业的面试体验和详细的评估报告。

## 核心能力

### 1. 智能面试流程
- **多阶段面试**：OPENING → MOTIVATION → PROJECT → BACKEND → LLM_RAG → BEHAVIOR → CLOSEOUT
- **智能追问**：根据候选人回答自动生成深度追问，针对关键技术词进行深挖
- **阶段管理**：自动推进面试阶段，确保面试流程完整
- **实时评分**：每轮回答后立即给出五个维度的评分

### 2. 高级功能
- **PDF 简历解析**：自动提取简历文本，分析候选人背景
- **JD 匹配**：根据岗位 JD 定制面试问题
- **实时流式对话**：AI 回复实时显示，提供流畅的对话体验
- **结构化输出**：每轮回复包含详细的反馈、评分和下一个问题
- **完整评估报告**：面试结束后生成 comprehensive 评估报告

### 3. 技术特性
- **会话隔离**：使用 Redis 存储会话数据，支持多用户并发
- **记忆管理**：永久保留系统提示和已问问题，滑动窗口管理对话历史
- **防注入声明**：保护系统免受简历和 JD 中的恶意指令影响
- **错误处理**：完善的错误处理机制，确保系统稳定运行

## 技术栈

### 后端
- **Java 17**：核心开发语言
- **Spring Boot 3.3.0**：应用框架
- **Spring AI Alibaba**：大语言模型集成
- **Redis**：会话存储和记忆管理
- **MySQL**：数据持久化
- **PDFBox**：PDF 文本提取

### 前端
- **Vue 3**：前端框架
- **Tailwind CSS**：样式框架
- **marked.js**：Markdown 渲染
- **SSE (Server-Sent Events)**：实时通信

### 大语言模型
- **通义千问**：通过 Spring AI Alibaba 集成

## 快速开始

### 环境要求
- JDK 17+
- Maven 3.8+
- Redis 7.0+
- MySQL 8.0+
- 通义千问 API Key

### 安装和运行

1. **克隆项目**
   ```bash
   git clone https://github.com/nbfys/ai-interview.git
   cd ai-interview-assistant
   ```

2. **配置环境变量**
   ```bash
   # Linux/Mac
   export DASHSCOPE_API_KEY=your_api_key_here
   
   # Windows
   set DASHSCOPE_API_KEY=your_api_key_here
   ```

3. **创建数据库**
   ```sql
   CREATE DATABASE interview_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   ```

4. **构建项目**
   ```bash
   mvn clean package
   ```

5. **运行项目**
   ```bash
   java -jar target/ai-interview-assistant-1.0.0.jar
   ```

6. **访问系统**
   打开浏览器，访问 http://localhost:8081

## 项目结构

```
ai-interview-assistant/
├── src/
│   ├── main/
│   │   ├── java/com/example/aiinterview/
│   │   │   ├── controller/         # 控制器
│   │   │   ├── service/            # 服务层
│   │   │   ├── util/              # 工具类
│   │   │   └── Application.java   # 应用入口
│   │   ├── resources/
│   │   │   ├── prompts/           # 系统提示
│   │   │   ├── static/            # 静态资源
│   │   │   └── application.yml    # 应用配置
│   └── test/                      # 测试代码
├── .trae/                         # Trae 配置
│   ├── skills/                    # 技能定义
│   └── rules/                     # 规则定义
├── pom.xml                        # Maven 配置
└── README.md                      # 项目说明
```

## 配置说明

### 应用配置 (application.yml)

```yaml
spring:
  application:
    name: ai-interview-assistant
  ai:
    dashscope:
      api-key: ${DASHSCOPE_API_KEY}
  redis:
    host: localhost
    port: 6379
  datasource:
    url: jdbc:mysql://localhost:3306/interview_db
    username: root
    password: root
    driver-class-name: com.mysql.cj.jdbc.Driver

server:
  port: 8081
```

### 环境变量

| 变量名 | 描述 | 示例值 |
|-------|------|--------|
| DASHSCOPE_API_KEY | 通义千问 API Key | sk-xxxxxxxxxxxxxxxxxxxxxxxx |

## 部署指南

### 本地部署

按照「快速开始」部分的步骤进行操作。

### 容器部署

1. **构建 Docker 镜像**
   ```bash
   docker build -t ai-interview-assistant .
   ```

2. **运行容器**
   ```bash
   docker run -d \
     --name ai-interview-assistant \
     -p 8081:8081 \
     -e DASHSCOPE_API_KEY=your_api_key_here \
     -e SPRING_REDIS_HOST=redis \
     -e SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/interview_db \
     ai-interview-assistant
   ```

### 云服务部署

1. **部署到 Kubernetes**
   - 创建 Kubernetes 部署配置
   - 配置环境变量和服务
   - 部署应用

2. **部署到云平台**
   - 支持部署到 AWS、Azure、阿里云等云平台
   - 配置相应的云服务（如 ElastiCache for Redis、RDS for MySQL）

## 贡献指南

1. **Fork 项目**
2. **创建功能分支**
   ```bash
   git checkout -b feature/your-feature-name
   ```
3. **提交更改**
   ```bash
   git commit -m "Add your feature"
   ```
4. **推送到远程**
   ```bash
   git push origin feature/your-feature-name
   ```
5. **创建 Pull Request**

## 许可证

本项目采用 MIT 许可证 - 详见 [LICENSE](LICENSE) 文件

## 联系方式

- 项目维护者：nbfys
- GitHub：https://github.com/nbfys/ai-interviewer

---

**注意**：本项目使用通义千问 API，需要有效的 API Key 才能正常运行。请确保在使用前获取并配置好 API Key。