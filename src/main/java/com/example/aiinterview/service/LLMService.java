package com.example.aiinterview.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class LLMService {

    // 直接注入 Spring AI 的 ChatClient，无需手写 HTTP
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    @Value("${spring.ai.dashscope.api-key:}")
    private String apiKey;

    @Value("classpath:prompts/interviewer.md")
    private Resource interviewerPromptResource;

    // 通过构造器注入 ChatClient.Builder（Spring AI 标准用法）
    public LLMService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = new ObjectMapper();
    }

    // 启动自检：检查 API Key 是否配置
    @PostConstruct
    public void validateConfig() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("DashScope API Key 未配置，请在环境变量 DASHSCOPE_API_KEY 中设置");
        }
        System.out.println("[LLMService] LLM 服务启动成功，使用 DashScope 模型。");
    }

    /**
     * 普通同步调用（用于 /evaluate 评估报告等非流式场景）
     */
    public String generateResponse(
            String systemPrompt,
            String userMessage,
            List<String> history) {

        // 构建完整消息列表
        List<Message> messages = buildMessages(systemPrompt, userMessage, history);

        try {
            // Spring AI 标准调用，内部自动处理 DashScope 协议
            return chatClient
                    .prompt(new Prompt(messages))
                    .call()
                    .content();

        } catch (Exception e) {
            // 区分运行时错误（不是配置错误），抛出业务异常让 Controller 处理
            throw new RuntimeException("LLM 调用失败：" + e.getMessage(), e);
        }
    }

    /**
     * 流式调用（用于 /chat/stream SSE 场景）
     */
    public Flux<String> generateStream(
            String systemPrompt,
            String userMessage,
            List<String> history) {

        List<Message> messages = buildMessages(systemPrompt, userMessage, history);

        try {
            // Spring AI 流式调用，返回 Flux<String>
            return chatClient
                    .prompt(new Prompt(messages))
                    .stream()
                    .content();

        } catch (Exception e) {
            return Flux.error(new RuntimeException("LLM 流式调用失败：" + e.getMessage(), e));
        }
    }

    /**
     * 构建 Spring AI 消息列表
     */
    private List<Message> buildMessages(
            String systemPrompt,
            String userMessage,
            List<String> history) {

        List<Message> messages = new ArrayList<>();

        // 1. System Prompt（永久保留，永不裁剪）
        messages.add(new SystemMessage(systemPrompt));

        // 2. 历史记录（滑动窗口，最近 10 条）
        List<String> trimmedHistory = trimHistory(history, 10);
        for (String msg : trimmedHistory) {
            if (msg.startsWith("用户：")) {
                messages.add(new UserMessage(msg.substring(3)));
            } else if (msg.startsWith("AI：")) {
                messages.add(new AssistantMessage(msg.substring(3)));
            }
        }

        // 3. 本轮用户消息
        if (userMessage != null && !userMessage.isBlank()) {
            messages.add(new UserMessage(userMessage));
        }

        return messages;
    }

    /**
     * 滑动窗口裁剪：只保留最近 maxCount 条历史
     */
    private List<String> trimHistory(List<String> history, int maxCount) {
        if (history == null || history.isEmpty()) return new ArrayList<>();
        
        // 加日志确认顺序已正确
        System.out.println("[DEBUG] history 总条数：" + history.size());
        System.out.println("[DEBUG] history[0]：" + 
            history.get(0).substring(0, Math.min(30, history.get(0).length())));
        System.out.println("[DEBUG] history[last]：" + 
            history.get(history.size()-1).substring(0, Math.min(30, history.get(history.size()-1).length())));
        
        // rightPush 修复后，history 已是正序（旧→新）
        // 取末尾 maxCount 条 = 最近的对话，顺序正确
        int start = Math.max(0, history.size() - maxCount);
        return new ArrayList<>(history.subList(start, history.size()));
    }

    /**
     * 解析 LLM 结构化输出（提取末尾 JSON 块）
     */
    public InterviewResponse parseStructuredOutput(String rawResponse) {
        try {
            // 提取最后一个完整 JSON 块
            Pattern pattern = Pattern.compile("\\{[\\s\\S]*\\}", Pattern.DOTALL);
            Matcher matcher = pattern.matcher(rawResponse);

            String lastJson = null;
            while (matcher.find()) {
                lastJson = matcher.group(); // 取最后匹配的 JSON
            }

            if (lastJson != null) {
                return objectMapper.readValue(lastJson, InterviewResponse.class);
            }
        } catch (Exception e) {
            System.err.println("[LLMService] 结构化解析失败，使用降级默认值：" + e.getMessage());
        }

        // 降级：解析失败时返回安全默认值，不中断面试
        return buildDefaultResponse();
    }

    /**
     * 读取 System Prompt 文件（截取 PROMPT_START ~ PROMPT_END 之间的内容）
     */
    public String loadSystemPrompt() throws IOException {
        String raw = new String(
            interviewerPromptResource.getInputStream().readAllBytes(),
            StandardCharsets.UTF_8
        );

        int start = raw.indexOf("===PROMPT_START===");
        int end   = raw.indexOf("===PROMPT_END===");

        if (start != -1 && end != -1) {
            return raw.substring(start + "===PROMPT_START===".length(), end).strip();
        }

        // 如果没有标记，返回全文（兼容旧格式）
        System.err.println("[LLMService] interviewer.md 缺少 PROMPT 边界标记，使用全文内容");
        return raw.strip();
    }

    private InterviewResponse buildDefaultResponse() {
        Map<String, Integer> defaultScores = new HashMap<>();
        defaultScores.put("clarity",    5);
        defaultScores.put("depth",      5);
        defaultScores.put("evidence",   5);
        defaultScores.put("tradeoff",   5);
        defaultScores.put("retrospect", 5);

        return new InterviewResponse(
            List.of("正在分析您的回答", "请继续"),
            "请问您能详细介绍一下您在这个项目中承担的具体职责吗？",
            "PROJECT",
            false,
            List.of("项目经验"),
            defaultScores
        );
    }

    // ============================================================
    // InterviewResponse DTO（使用 Jackson 注解，支持直接反序列化）
    // ============================================================
    public static class InterviewResponse {
        @com.fasterxml.jackson.annotation.JsonProperty("feedback")
        private List<String> feedback;

        @com.fasterxml.jackson.annotation.JsonProperty("next_question")
        private String nextQuestion;

        @com.fasterxml.jackson.annotation.JsonProperty("stage")
        private String stage;

        @com.fasterxml.jackson.annotation.JsonProperty("should_move_stage")
        private boolean shouldMoveStage;

        @com.fasterxml.jackson.annotation.JsonProperty("question_tags")
        private List<String> questionTags;

        @com.fasterxml.jackson.annotation.JsonProperty("scores")
        private Map<String, Integer> scores;

        // 无参构造（Jackson 反序列化必须）
        public InterviewResponse() {}

        public InterviewResponse(
                List<String> feedback, String nextQuestion, String stage,
                boolean shouldMoveStage, List<String> questionTags,
                Map<String, Integer> scores) {
            this.feedback        = feedback;
            this.nextQuestion    = nextQuestion;
            this.stage           = stage;
            this.shouldMoveStage = shouldMoveStage;
            this.questionTags    = questionTags;
            this.scores          = scores;
        }

        public List<String> getFeedback()        { return feedback; }
        public String getNextQuestion()          { return nextQuestion; }
        public String getStage()                 { return stage; }
        public boolean isShouldMoveStage()       { return shouldMoveStage; }
        public List<String> getQuestionTags()    { return questionTags; }
        public Map<String, Integer> getScores()  { return scores; }
    }
}