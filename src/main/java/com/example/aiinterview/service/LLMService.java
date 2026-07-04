package com.example.aiinterview.service;

import com.example.aiinterview.service.client.ChatClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class LLMService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private ChatClient chatClient;

    @Value("classpath:prompts/interviewer.md")
    private Resource interviewerPromptResource;

    @PostConstruct
    public void validateConfig() {
        System.out.println("[LLMService] LLM 服务启动成功，已注入 ChatClient: " + chatClient.getClass().getSimpleName());
    }

    /**
     * 同步生成回答
     */
    public String generateResponse(
            String systemPrompt,
            String userMessage,
            List<String> history) {

        List<Map<String, String>> messages = buildApiMessages(systemPrompt, userMessage, history);
        return chatClient.chat(messages);
    }

    /**
     * 流式生成回答
     */
    public void generateStream(
            String systemPrompt,
            String userMessage,
            List<String> history,
            java.util.concurrent.BlockingQueue<String> queue) {

        List<Map<String, String>> messagesList = buildApiMessages(systemPrompt, userMessage, history);
        chatClient.stream(messagesList, queue);
    }

    /**
     * 构建 API 请求的消息列表（system prompt + 历史 + 当前问题）
     */
    private List<Map<String, String>> buildApiMessages(String systemPrompt, String userMessage, List<String> history) {
        List<Map<String, String>> messages = new ArrayList<>();

        Map<String, String> system = new HashMap<>();
        system.put("role", "system");
        system.put("content", systemPrompt);
        messages.add(system);

        List<String> trimmedHistory = trimHistory(history, 10);
        for (String msg : trimmedHistory) {
            Map<String, String> m = new HashMap<>();
            if (msg.startsWith("用户：")) {
                m.put("role", "user");
                m.put("content", msg.substring(3));
            } else if (msg.startsWith("AI：")) {
                m.put("role", "assistant");
                m.put("content", msg.substring(3));
            } else {
                continue;
            }
            messages.add(m);
        }

        if (userMessage != null && !userMessage.isBlank()) {
            Map<String, String> user = new HashMap<>();
            user.put("role", "user");
            user.put("content", userMessage);
            messages.add(user);
        }

        // 修复：只有 system 消息时（首个问题，用户还没发言），添加占位用户消息
        // 否则 DeepSeek 等 API 在只有 system 时输出不可控
        if (messages.size() == 1) {
            Map<String, String> placeholder = new HashMap<>();
            placeholder.put("role", "user");
            placeholder.put("content", "请开始面试");
            messages.add(placeholder);
        }

        return messages;
    }

    public List<Message> buildDualContextMessages(String baseSystemPrompt, String userMessage,
                                                   List<String> history, String candidateProfile) {
        List<Message> messages = new ArrayList<>();

        StringBuilder fullSystemPrompt = new StringBuilder(baseSystemPrompt);
        if (candidateProfile != null && !candidateProfile.isEmpty()) {
            fullSystemPrompt.append("\n\n").append(candidateProfile);
        }
        messages.add(new SystemMessage(fullSystemPrompt.toString()));

        List<String> trimmedHistory = trimHistory(history, 10);
        for (String msg : trimmedHistory) {
            if (msg.startsWith("用户：")) {
                messages.add(new UserMessage(msg.substring(3)));
            } else if (msg.startsWith("AI：")) {
                messages.add(new AssistantMessage(msg.substring(3)));
            }
        }

        if (userMessage != null && !userMessage.isBlank()) {
            messages.add(new UserMessage(userMessage));
        }

        return messages;
    }

    private List<String> trimHistory(List<String> history, int maxCount) {
        if (history == null || history.isEmpty()) return new ArrayList<>();
        int start = Math.max(0, history.size() - maxCount);
        return new ArrayList<>(history.subList(start, history.size()));
    }

    public InterviewResponse parseStructuredOutput(String rawResponse, String currentStage) {
        try {
            // 1. 先清理 markdown 代码块标记（```json ... ```）
            String cleaned = rawResponse.replaceAll("```[a-zA-Z]*\\s*", "").trim();

            // 2. 用正则提取 JSON 对象
            Pattern pattern = Pattern.compile("\\{[\\s\\S]*\\}", Pattern.DOTALL);
            Matcher matcher = pattern.matcher(cleaned);

            String lastJson = null;
            while (matcher.find()) {
                lastJson = matcher.group();
            }

            if (lastJson != null) {
                return objectMapper.readValue(lastJson, InterviewResponse.class);
            }

            System.err.println("[LLMService] 未找到 JSON 结构，原始响应前100字符: "
                    + rawResponse.substring(0, Math.min(100, rawResponse.length())));

        } catch (Exception e) {
            System.err.println("[LLMService] 结构化解析失败 (" + e.getMessage() + ")，原始响应前100字符: "
                    + rawResponse.substring(0, Math.min(100, rawResponse.length()))
                    + "，使用降级默认值");
        }

        return buildDefaultResponse(currentStage);
    }

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

        System.err.println("[LLMService] interviewer.md 缺少 PROMPT 边界标记，使用全文内容");
        return raw.strip();
    }

    private InterviewResponse buildDefaultResponse(String currentStage) {
        String stage = (currentStage != null) ? currentStage : "OPENING";
        String question = "请先做个自我介绍，包括您的技术背景和主要经验";
        if (!"OPENING".equals(stage)) {
            question = "请继续分享您的想法，还有哪些方面想深入谈谈？";
        }
        return new InterviewResponse(question, stage);
    }

    public static class InterviewResponse {
        @com.fasterxml.jackson.annotation.JsonProperty("next_question")
        private String nextQuestion;

        @com.fasterxml.jackson.annotation.JsonProperty("stage")
        private String stage;

        public InterviewResponse() {}

        public InterviewResponse(String nextQuestion, String stage) {
            this.nextQuestion = nextQuestion;
            this.stage = stage;
        }

        public String getNextQuestion() { return nextQuestion; }
        public String getStage()        { return stage; }
    }

    // Message types
    public interface Message {
        String getRole();
        String getContent();
    }

    public static class SystemMessage implements Message {
        private final String content;
        public SystemMessage(String content) { this.content = content; }
        public String getRole() { return "system"; }
        public String getContent() { return content; }
    }

    public static class UserMessage implements Message {
        private final String content;
        public UserMessage(String content) { this.content = content; }
        public String getRole() { return "user"; }
        public String getContent() { return content; }
    }

    public static class AssistantMessage implements Message {
        private final String content;
        public AssistantMessage(String content) { this.content = content; }
        public String getRole() { return "assistant"; }
        public String getContent() { return content; }
    }
}
