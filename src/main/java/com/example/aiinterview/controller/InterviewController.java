package com.example.aiinterview.controller;

import com.example.aiinterview.common.Result;
import com.example.aiinterview.config.InterviewConfig;
import com.example.aiinterview.service.IntentDetectionService;
import com.example.aiinterview.service.InterviewStateService;
import com.example.aiinterview.service.LLMService;
import com.example.aiinterview.entity.InterviewSession;
import com.example.aiinterview.service.InterviewSessionService;
import com.example.aiinterview.service.MetadataExtractionService;
import com.example.aiinterview.service.RedisMemoryService;
import com.example.aiinterview.util.PdfUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.Resource;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

import com.fasterxml.jackson.databind.ObjectMapper;

@Slf4j
@RestController
@RequestMapping("/api/interview")
public class InterviewController {

    @Resource
    private RedisMemoryService redisMemoryService;

    @Resource
    private LLMService llmService;

    @Resource
    private InterviewStateService interviewStateService;

    @Resource
    private IntentDetectionService intentDetectionService;

    @Resource
    private MetadataExtractionService metadataExtractionService;

    @Resource
    private com.example.aiinterview.config.RateLimiter rateLimiter;

    @Resource
    private ThreadPoolExecutor sseExecutor;

    @Resource
    private InterviewConfig interviewConfig;

    @Resource
    private InterviewSessionService interviewSessionService;

    private static final List<String> STAGES = Arrays.asList(
            "OPENING", "MOTIVATION", "PROJECT", "BACKEND", "LLM_RAG", "BEHAVIOR", "CLOSEOUT"
    );

    private String getNextStage(String currentStage) {
        int index = STAGES.indexOf(currentStage);
        if (index < STAGES.size() - 1) {
            return STAGES.get(index + 1);
        }
        return currentStage;
    }

    @PostMapping("/init")
    public Result<Map<String, Object>> init(@RequestParam("resume") MultipartFile resume,
                                            @RequestParam("jdContent") String jdContent) {
        if (resume == null || resume.isEmpty()) {
            return Result.error("简历不能为空");
        }
        if (jdContent == null || jdContent.trim().isEmpty()) {
            return Result.error("JD内容不能为空");
        }

        try (InputStream inputStream = resume.getInputStream()) {
            String resumeContent = PdfUtil.extractText(inputStream);
            String sessionId = UUID.randomUUID().toString();

            // 简历/JD 规则清洗，移除潜在注入内容
            jdContent = intentDetectionService.sanitizeContent(jdContent);
            resumeContent = intentDetectionService.sanitizeContent(resumeContent);

            redisMemoryService.cacheJdContent(sessionId, jdContent);
            redisMemoryService.cacheResumeContent(sessionId, resumeContent);

            String interviewerPrompt = llmService.loadSystemPrompt();
            StringBuilder systemPrompt = new StringBuilder();
            systemPrompt.append(interviewerPrompt).append("\n\n");
            systemPrompt.append("# 面试上下文\n");
            systemPrompt.append("## 岗位 JD\n").append(jdContent).append("\n\n");
            systemPrompt.append("## 候选人简历\n").append(resumeContent).append("\n");

            redisMemoryService.cacheSystemPrompt(sessionId, systemPrompt.toString());
            interviewStateService.initState(sessionId);

            // 持久化到MySQL
            InterviewSession dbSession = new InterviewSession();
            dbSession.setSessionId(sessionId);
            dbSession.setJdContent(jdContent);
            dbSession.setResumeContent(resumeContent);
            dbSession.setStatus("IN_PROGRESS");
            dbSession.setCreateTime(java.time.LocalDateTime.now());
            dbSession.setExpireTime(java.time.LocalDateTime.now().plusDays(30));
            interviewSessionService.insert(dbSession);

            Map<String, Object> result = new HashMap<>();
            result.put("sessionId", sessionId);
            result.put("mode", "LLM");
            log.info("面试会话初始化成功，sessionId: {}", sessionId);
            return Result.success(result);
        } catch (Exception e) {
            log.error("初始化面试会话失败", e);
            return Result.error("初始化失败：" + e.getMessage());
        }
    }

    @PostMapping("/chat/send")
    public Result<Map<String, String>> send(@RequestBody Map<String, String> request) {
        try {
            String sessionId = request.get("sessionId");
            String userMessage = request.get("userMessage");

            if (sessionId == null || sessionId.isEmpty()) {
                return Result.error("sessionId不能为空");
            }
            if (userMessage == null || userMessage.isEmpty()) {
                return Result.error("消息内容不能为空");
            }

            String messageId = UUID.randomUUID().toString();
            redisMemoryService.addMessage(sessionId, "用户：" + userMessage);
            log.debug("用户消息已保存，sessionId: {}", sessionId);

            Map<String, String> result = new HashMap<>();
            result.put("messageId", messageId);
            return Result.success(result);
        } catch (Exception e) {
            log.error("发送消息失败", e);
            return Result.error("发送失败：" + e.getMessage());
        }
    }

    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam("sessionId") String sessionId,
                             @RequestParam(value = "messageId", required = false) String messageId) {
        SseEmitter emitter = new SseEmitter(interviewConfig.getSseTimeoutMs());

        if (!rateLimiter.tryAcquire()) {
            handleRateLimitExceeded(emitter);
            return emitter;
        }

        CompletableFuture.runAsync(() -> {
            try {
                processStreamRequest(sessionId, emitter);
            } catch (Exception e) {
                log.error("处理流式请求异常，sessionId: {}", sessionId, e);
                handleStreamError(emitter, "AI 服务暂时不可用，请稍后重试");
            }
        }, sseExecutor);

        return emitter;
    }

    private void handleRateLimitExceeded(SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().name("error").data("请求过于频繁，请稍后再试"));
            emitter.complete();
        } catch (Exception e) {
            log.error("发送限流响应失败", e);
        }
    }

    private void handleStreamError(SseEmitter emitter, String message) {
        try {
            emitter.send(SseEmitter.event().name("error").data(message));
            emitter.completeWithError(new RuntimeException(message));
        } catch (Exception e) {
            log.error("发送错误响应失败", e);
        }
    }

    private void processStreamRequest(String sessionId, SseEmitter emitter) {
        try {
            String jdContent = redisMemoryService.getJdContent(sessionId);
            String resumeContent = redisMemoryService.getResumeContent(sessionId);

            if (jdContent == null || resumeContent == null) {
                emitter.send(SseEmitter.event().data("会话不存在或已过期"));
                emitter.complete();
                return;
            }

            String systemPrompt = redisMemoryService.getSystemPrompt(sessionId);
            if (systemPrompt == null) {
                String interviewerPrompt = llmService.loadSystemPrompt();
                StringBuilder prompt = new StringBuilder();
                prompt.append(interviewerPrompt).append("\n\n");
                prompt.append("# 面试上下文\n");
                prompt.append("## 岗位 JD\n").append(jdContent).append("\n\n");
                prompt.append("## 候选人简历\n").append(resumeContent).append("\n");
                systemPrompt = prompt.toString();
                redisMemoryService.cacheSystemPrompt(sessionId, systemPrompt);
            }

            List<String> history = redisMemoryService.getHistory(sessionId);
            Map<String, Object> state = interviewStateService.getState(sessionId);
            String currentStage = (String) state.get("stage");
            int stageTurnCount = (int) state.getOrDefault("stageTurnCount", 0);
            int totalTurns = (int) state.getOrDefault("totalTurns", 0);

            String userMessage = null;
            boolean isFirstQuestion = history.isEmpty();

            if (!history.isEmpty()) {
                String lastMsg = history.get(history.size() - 1);
                if (lastMsg.startsWith("用户：")) {
                    userMessage = lastMsg.substring(3);
                }
            }

            // Intent detection → 生成上下文提示给 LLM（不再阻断）
            String intentContextHint = "";
            if (userMessage != null && !userMessage.isEmpty()) {
                IntentDetectionService.IntentResult intentResult =
                        intentDetectionService.detectIntent(userMessage, currentStage);
                log.debug("意图检测结果，sessionId: {}, intent: {}", sessionId, intentResult.getIntentType());
                if (intentResult.getContextHint() != null) {
                    intentContextHint = "\n\n[系统观察]\n" + intentResult.getContextHint() + "\n";
                }
            }

            Set<Object> askedQuestionsSet = redisMemoryService.getAskedQuestions(sessionId);
            List<String> askedQuestions = new ArrayList<>();
            if (askedQuestionsSet != null) {
                for (Object question : askedQuestionsSet) {
                    askedQuestions.add(question.toString());
                }
            }

            StringBuilder completePrompt = buildCompletePrompt(systemPrompt, askedQuestions,
                    currentStage, stageTurnCount, totalTurns, isFirstQuestion);
            completePrompt.append(intentContextHint);

            // CLOSEOUT 最后轮次：通知 LLM 生成结束语
            if ("CLOSEOUT".equals(currentStage) && stageTurnCount >= 1
                    && userMessage != null && !userMessage.isEmpty()) {
                completePrompt.append("\n[系统提示：这是最后一轮对话。请先认真回答候选人提出的所有问题，不要忽略候选人的提问，回答完毕后自然收尾结束面试]\n");
            }

            handleNormalResponse(sessionId, emitter, completePrompt.toString(), userMessage,
                    history, currentStage, stageTurnCount, totalTurns);

        } catch (Exception e) {
            log.error("处理流式请求异常，sessionId: {}", sessionId, e);
            handleStreamError(emitter, "AI 服务暂时不可用，请稍后重试");
        }
    }

    private StringBuilder buildCompletePrompt(String systemPrompt, List<String> askedQuestions,
                                             String currentStage, int stageTurnCount, int totalTurns, boolean isFirstQuestion) {
        StringBuilder completePrompt = new StringBuilder();
        completePrompt.append(systemPrompt).append("\n\n");

        if (isFirstQuestion) {
            completePrompt.append("[特别指令]\n");
            completePrompt.append("这是面试的第一个问题，必须先让候选人做自我介绍！\n");
            completePrompt.append("请生成一个友好的开场，引导候选人介绍自己的背景。\n\n");
        }

        completePrompt.append("[已问问题列表]\n");
        completePrompt.append("askedQuestions: [");
        List<String> recentAskedQuestions = askedQuestions.size() > 5
                ? askedQuestions.subList(askedQuestions.size() - 5, askedQuestions.size())
                : askedQuestions;
        for (int i = 0; i < recentAskedQuestions.size(); i++) {
            completePrompt.append("\"").append(recentAskedQuestions.get(i)).append("\"");
            if (i < recentAskedQuestions.size() - 1) {
                completePrompt.append(", ");
            }
        }
        completePrompt.append("]\n\n");
        completePrompt.append("[当前面试状态]\n");
        completePrompt.append("stage: ").append(currentStage).append("\n");
        completePrompt.append("stageTurnCount: ").append(stageTurnCount).append("\n");
        completePrompt.append("totalTurns: ").append(totalTurns).append("\n\n");

        return completePrompt;
    }

    private void handleNormalResponse(String sessionId, SseEmitter emitter, String completePrompt,
                                       String userMessage, List<String> history, String currentStage,
                                       int stageTurnCount, int totalTurns) throws Exception {
        log.debug("开始流式调用LLM服务，sessionId: {}", sessionId);

        java.util.concurrent.BlockingQueue<String> queue = new java.util.concurrent.ArrayBlockingQueue<>(1);

        java.util.concurrent.CompletableFuture.runAsync(() ->
                llmService.generateStream(completePrompt, userMessage, history, queue),
                sseExecutor
        );

        StringBuilder fullBuffer = new StringBuilder();
        try {
            while (true) {
                String chunk = queue.take();
                if ("__DONE__".equals(chunk)) break;
                if (chunk.startsWith("__ERROR__:")) {
                    throw new RuntimeException(chunk.substring(9));
                }
                if (chunk.isEmpty()) continue;
                fullBuffer.append(chunk);
            }
        } catch (Exception e) {
            log.error("LLM流式调用异常，sessionId: {}", sessionId, e);
        }

        String fullAiResponse = fullBuffer.toString();
        log.debug("LLM流式调用成功，响应长度: {}", fullAiResponse.length());

        // LLM输出后置检查：仅记录警告，不替换输出
        if (!intentDetectionService.checkOutput(fullAiResponse)) {
            log.warn("LLM输出可能包含异常内容，但仍正常使用（仅记录日志），sessionId: {}", sessionId);
        }

        // 提取显示内容（去掉末尾JSON部分）
        String cleanDisplayContent = stripJsonBlock(fullAiResponse);

        // 解析结构化输出
        LLMService.InterviewResponse interviewResponse =
                llmService.parseStructuredOutput(fullAiResponse, currentStage);
        String nextQuestion = interviewResponse.getNextQuestion();
        String llmStage = interviewResponse.getStage();

        // 问题去重检查（仅记录，不重试）
        Set<Object> existingQuestions = redisMemoryService.getAskedQuestions(sessionId);
        if (!isFollowUpPhrase(nextQuestion) && isQuestionDuplicate(nextQuestion, existingQuestions)) {
            log.warn("检测到重复问题，但由LLM自行处理，sessionId: {}, 问题: {}", sessionId, nextQuestion);
        }

        // 存储到Redis
        redisMemoryService.addMessage(sessionId, "AI：" + cleanDisplayContent);
        if (!isFollowUpPhrase(nextQuestion)) {
            redisMemoryService.addAskedQuestion(sessionId, nextQuestion);
        }

        if (userMessage != null) {
            metadataExtractionService.extractAndUpdateMetadata(sessionId, userMessage, cleanDisplayContent);
        }

        // 状态更新：优先使用LLM决定的stage
        String updatedStage = (llmStage != null && STAGES.contains(llmStage)) ? llmStage : currentStage;
        int updatedTotalTurns = totalTurns + 1;
        int updatedStageTurnCount = updatedStage.equals(currentStage) ? stageTurnCount + 1 : 1;

        Map<String, Object> newState = new HashMap<>();
        newState.put("stage", updatedStage);
        newState.put("stageTurnCount", updatedStageTurnCount);
        newState.put("totalTurns", updatedTotalTurns);
        interviewStateService.updateState(sessionId, newState);

        // 流式输出问题（逐字推送）
        if (nextQuestion != null && !nextQuestion.isBlank()) {
            for (char c : nextQuestion.toCharArray()) {
                try {
                    emitter.send(SseEmitter.event().name("token").data(String.valueOf(c)));
                    Thread.sleep(15);
                } catch (Exception e) {
                    log.debug("流式输出被中断，sessionId: {}", sessionId);
                    break;
                }
            }
        }

        // 发送状态更新（无评分）
        sendStatusUpdate(emitter, updatedStage, updatedStageTurnCount, updatedTotalTurns, nextQuestion);
        checkAndSendEndSignal(emitter, updatedStage, updatedStageTurnCount, updatedTotalTurns);

        // 持久化
        List<String> currentHistory = redisMemoryService.getHistory(sessionId);
        interviewSessionService.updateBySessionId(sessionId, "IN_PROGRESS", null);
        interviewSessionService.asyncSaveHistory(sessionId, currentHistory);

        log.debug("流式请求处理完成，sessionId: {}", sessionId);
        emitter.complete();
    }
    
    private void sendStatusUpdate(SseEmitter emitter, String stage, int stageTurnCount,
                                  int totalTurns, String nextQuestion) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            Map<String, Object> statusUpdate = new HashMap<>();
            statusUpdate.put("stage", stage);
            statusUpdate.put("stageTurnCount", stageTurnCount);
            statusUpdate.put("totalTurns", totalTurns);
            statusUpdate.put("next_question", nextQuestion);
            String statusJson = objectMapper.writeValueAsString(statusUpdate);
            emitter.send(SseEmitter.event().name("status").data(statusJson));
        } catch (Exception e) {
            log.error("发送status事件失败", e);
        }
    }

    private boolean shouldEndInterview(String stage, int stageTurnCount, int totalTurns) {
        // 情况1：达到最大轮次
        if (totalTurns > interviewConfig.getMaxTotalTurns()) {
            return true;
        }

        // 情况2：在CLOSEOUT阶段且完成了至少2轮对话
        if ("CLOSEOUT".equals(stage) && stageTurnCount >= 2) {
            return true;
        }

        return false;
    }
    
    private void checkAndSendEndSignal(SseEmitter emitter, String stage, int stageTurnCount, int totalTurns) {
        try {
            boolean shouldEnd = shouldEndInterview(stage, stageTurnCount, totalTurns);

            if (shouldEnd) {
                emitter.send(SseEmitter.event().name("end").data("面试结束"));
            }
        } catch (Exception e) {
            log.error("发送结束信号失败", e);
        }
    }

    @GetMapping("/resume")
    public Result<Map<String, Object>> resumeInterview(@RequestParam("sessionId") String sessionId) {
        try {
            if (sessionId == null || sessionId.isEmpty()) {
                return Result.error("sessionId不能为空");
            }

            Map<String, Object> state = interviewStateService.getState(sessionId);
            if (state == null || state.isEmpty()) {
                // 从MySQL恢复会话
                InterviewSession dbSession = interviewSessionService.selectBySessionId(sessionId);
                if (dbSession == null) {
                    return Result.error("会话不存在或已过期");
                }
                // 重建Redis缓存
                redisMemoryService.cacheJdContent(sessionId, dbSession.getJdContent());
                redisMemoryService.cacheResumeContent(sessionId, dbSession.getResumeContent());
                interviewStateService.initState(sessionId);

                if (dbSession.getConversationHistory() != null) {
                    try {
                        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                        java.util.List<String> historyList = mapper.readValue(
                            dbSession.getConversationHistory(),
                            com.fasterxml.jackson.databind.type.TypeFactory.defaultInstance().constructCollectionType(
                                java.util.List.class, String.class)
                        );
                        for (String msg : historyList) {
                            redisMemoryService.addMessage(sessionId, msg);
                        }
                    } catch (Exception e) {
                        log.warn("从MySQL恢复对话历史失败，sessionId: {}", sessionId, e);
                    }
                }

                state = interviewStateService.getState(sessionId);
            }

            List<String> history = redisMemoryService.getHistory(sessionId);
            MetadataExtractionService.CandidateProfile profile =
                    metadataExtractionService.getCandidateProfile(sessionId);

            Map<String, Object> result = new HashMap<>();
            result.put("state", state);
            result.put("history", history);

            if (!history.isEmpty()) {
                result.put("lastMessage", history.get(history.size() - 1));
            }

            if (profile != null) {
                result.put("profile", profile);
            }

            log.debug("恢复会话成功，sessionId: {}", sessionId);
            return Result.success(result);
        } catch (Exception e) {
            log.error("恢复会话失败，sessionId: {}", sessionId, e);
            return Result.error("恢复会话失败：" + e.getMessage());
        }
    }

    @PostMapping("/evaluate")
    public Result<Map<String, Object>> evaluate(@RequestBody Map<String, String> request) {
        try {
            String sessionId = request.get("sessionId");
            if (sessionId == null || sessionId.isEmpty()) {
                return Result.error("sessionId不能为空");
            }

            List<String> history = redisMemoryService.getHistory(sessionId);
            MetadataExtractionService.CandidateProfile profile =
                    metadataExtractionService.getCandidateProfile(sessionId);

            StringBuilder prompt = new StringBuilder();
            prompt.append("# 面试评估\n");
            prompt.append("请根据以下对话历史，对候选人进行全面评估：\n\n");

            if (profile != null) {
                prompt.append(profile.toPromptString()).append("\n");
            }

            for (String msg : history) {
                prompt.append(msg).append("\n");
            }

            prompt.append("\n评估要求：\n");
            prompt.append("- 必须基于对话历史中的具体内容进行评估\n");
            prompt.append("- 每一项评分都必须引用对话中的例子\n");
            prompt.append("- 亮点和薄弱点必须具体、可验证\n");
            prompt.append("- 避免泛泛而谈，确保评估结果客观公正\n");
            prompt.append("- 评分要符合实际表现，避免全是高分\n");

            prompt.append("\n评估维度：\n");
            prompt.append("- 表达清晰度（1-10）\n");
            prompt.append("- 技术深度（1-10）\n");
            prompt.append("- 证据与量化（1-10）\n");
            prompt.append("- 权衡取舍意识（1-10）\n");
            prompt.append("- 复盘与改进意识（1-10）\n");

            prompt.append("\n请直接输出一段结构化的评估报告，包含：\n");
            prompt.append("1. 总体评分和评价\n");
            prompt.append("2. 各维度的详细评分和说明\n");
            prompt.append("3. 候选人的亮点和优势\n");
            prompt.append("4. 需要改进的地方\n");
            prompt.append("5. 具体的建议和下一步发展方向\n\n");
            prompt.append("请用清晰的Markdown格式输出，不要用JSON格式。\n");

            String systemPrompt = llmService.loadSystemPrompt();
            String response = llmService.generateResponse(systemPrompt, prompt.toString(), new ArrayList<>());

            log.debug("评估报告生成成功，sessionId: {}", sessionId);

            redisMemoryService.cleanSession(sessionId);

            Map<String, Object> result = new HashMap<>();
            result.put("report", response);
            return Result.success(result);
        } catch (Exception e) {
            log.error("评估失败", e);
            return Result.error("评估失败：" + e.getMessage());
        }
    }

    private String stripJsonBlock(String text) {
        if (text == null || text.isBlank()) return text;

        int lastBrace = text.lastIndexOf('}');
        if (lastBrace == -1) return text;

        int braceCount = 1;
        int startBrace = -1;
        for (int i = lastBrace - 1; i >= 0; i--) {
            if (text.charAt(i) == '}') braceCount++;
            if (text.charAt(i) == '{') braceCount--;
            if (braceCount == 0) {
                startBrace = i;
                break;
            }
        }

        if (startBrace == -1) return text;

        String possibleJson = text.substring(startBrace, lastBrace + 1);
        if (possibleJson.contains("\"feedback\"") &&
                possibleJson.contains("\"next_question\"")) {
            String contentBefore = text.substring(0, startBrace).trim();
            return contentBefore.isEmpty() ? text : contentBefore;
        }
        return text;
    }

    /**
     * 判断是否是纯追问话术（不算重复问题）
     */
    private boolean isFollowUpPhrase(String text) {
        if (text == null || text.isBlank()) return false;
        
        String normalized = text.toLowerCase().trim();
        
        // 纯追问话术列表
        Set<String> followUpPhrases = Set.of(
            "请展开说明",
            "请详细说明",
            "能详细说说吗",
            "还有什么补充",
            "请继续",
            "能否详细介绍",
            "可以具体说说吗",
            "请阐述一下",
            "您能解释一下吗",
            "对这个问题还有什么见解",
            "能详细讲讲吗",
            "请详细说说",
            "请具体说明"
        );
        
        // 完全匹配或高度相似
        for (String phrase : followUpPhrases) {
            if (normalized.contains(phrase) && normalized.length() < phrase.length() + 15) {
                return true;
            }
        }
        
        // 正则匹配：只有"请/能/可以"开头 + "说/讲/介绍" + "吗/一下"结尾
        String pattern = "^(请|能否?|可以).{0,8}(说|讲|介绍|阐述|解释|展开).{0,5}(吗|一下)?[？?。]*$";
        if (normalized.matches(pattern)) {
            return true;
        }
        
        return false;
    }

    /**
     * 标准化问题字符串，用于去重比较
     */
    private String normalizeQuestion(String question) {
        if (question == null) return "";
        // 1. 转小写，去首尾空格
        String normalized = question.toLowerCase().trim();
        // 2. 替换所有空白字符为单个空格
        normalized = normalized.replaceAll("\\s+", " ");
        // 3. 去除常见的标点符号
        normalized = normalized.replaceAll("[，。？！,.!?]", "");
        // 4. 去除多余的空格
        return normalized.trim();
    }

    /**
     * 判断问题是否重复（使用标准化字符串比较）
     */
    private boolean isQuestionDuplicate(String newQuestion, Set<Object> existingQuestions) {
        if (newQuestion == null || existingQuestions == null || existingQuestions.isEmpty()) {
            return false;
        }
        
        String normalizedNew = normalizeQuestion(newQuestion);
        
        for (Object existingQuestion : existingQuestions) {
            String normalizedExisting = normalizeQuestion(existingQuestion.toString());
            
            // 完全匹配（标准化后）
            if (normalizedNew.equals(normalizedExisting)) {
                return true;
            }
            
            // 高度相似匹配（包含关系且长度接近）
            if (normalizedNew.contains(normalizedExisting) || normalizedExisting.contains(normalizedNew)) {
                int lenDiff = Math.abs(normalizedNew.length() - normalizedExisting.length());
                if (lenDiff <= 20) { // 长度差异不大
                    return true;
                }
            }
        }
        
        return false;
    }
}
