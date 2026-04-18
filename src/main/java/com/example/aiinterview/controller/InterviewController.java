package com.example.aiinterview.controller;

import com.example.aiinterview.common.Result;
import com.example.aiinterview.entity.InterviewSession;
import com.example.aiinterview.service.InterviewStateService;
import com.example.aiinterview.service.LLMService;
import com.example.aiinterview.service.RedisMemoryService;
import com.example.aiinterview.util.PdfUtil;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.*;
import java.util.Set;
import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/interview")
public class InterviewController {

    @Resource
    private RedisMemoryService redisMemoryService;
    
    @Resource
    private LLMService llmService;
    
    @Resource
    private InterviewStateService interviewStateService;

    // 会话存储
    private Map<String, SessionInfo> sessionMap = new HashMap<>();

    // 会话信息类
    private static class SessionInfo {
        private String jdContent;
        private String resumeContent;
        private Date createTime;

        public SessionInfo(String jdContent, String resumeContent) {
            this.jdContent = jdContent;
            this.resumeContent = resumeContent;
            this.createTime = new Date();
        }

        public String getJdContent() {
            return jdContent;
        }

        public String getResumeContent() {
            return resumeContent;
        }
    }

    // 面试阶段顺序
    private static final List<String> STAGES = Arrays.asList(
        "OPENING", "MOTIVATION", "PROJECT", "BACKEND", "LLM_RAG", "BEHAVIOR", "CLOSEOUT"
    );

    // 获取下一个面试阶段
    private String getNextStage(String currentStage) {
        int index = STAGES.indexOf(currentStage);
        if (index < STAGES.size() - 1) {
            return STAGES.get(index + 1);
        }
        return currentStage;
    }

    // 初始化面试会话
    @PostMapping("/init")
    public Result<Map<String, Object>> init(@RequestParam("resume") MultipartFile resume, 
                                           @RequestParam("jdContent") String jdContent) {
        try {
            // 提取 PDF 文本
            String resumeContent = PdfUtil.extractText(resume.getInputStream());
            
            // 生成 sessionId
            String sessionId = UUID.randomUUID().toString();
            
            // 保存到内存
            sessionMap.put(sessionId, new SessionInfo(jdContent, resumeContent));
            
            // 读取面试官提示
            String interviewerPrompt = llmService.loadSystemPrompt();
            
            // 构建系统提示
            StringBuilder systemPrompt = new StringBuilder();
            systemPrompt.append(interviewerPrompt).append("\n\n");
            systemPrompt.append("# 面试上下文\n");
            systemPrompt.append("## 岗位 JD\n").append(jdContent).append("\n\n");
            systemPrompt.append("## 候选人简历\n").append(resumeContent).append("\n");
            
            // 缓存系统提示
            redisMemoryService.cacheSystemPrompt(sessionId, systemPrompt.toString());
            
            // 初始化面试状态
            interviewStateService.initState(sessionId);
            
            Map<String, Object> result = new HashMap<>();
            result.put("sessionId", sessionId);
            result.put("mode", "LLM"); // 模式标识
            return Result.success(result);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("初始化失败：" + e.getMessage());
        }
    }

    // 发送消息
    @PostMapping("/chat/send")
    public Result<Map<String, String>> send(@RequestBody Map<String, String> request) {
        try {
            String sessionId = request.get("sessionId");
            String userMessage = request.get("userMessage");
            
            // 生成 messageId
            String messageId = UUID.randomUUID().toString();
            
            // 保存用户消息
            redisMemoryService.addMessage(sessionId, "用户：" + userMessage);
            
            Map<String, String> result = new HashMap<>();
            result.put("messageId", messageId);
            return Result.success(result);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("发送失败：" + e.getMessage());
        }
    }

    // SSE 流式对话
    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam("sessionId") String sessionId, 
                             @RequestParam(value = "messageId", required = false) String messageId) {
        SseEmitter emitter = new SseEmitter(300000L); // 5分钟超时
        
        // 异步处理
        new Thread(() -> {
            try {
                // 查询会话信息
                SessionInfo session = sessionMap.get(sessionId);
                
                if (session == null) {
                    emitter.send(SseEmitter.event().data("会话不存在"));
                    emitter.complete();
                    return;
                }
                
                // 获取系统提示
                String systemPrompt = redisMemoryService.getSystemPrompt(sessionId);
                if (systemPrompt == null) {
                    // 重新构建系统提示
                    String interviewerPrompt = llmService.loadSystemPrompt();
                    StringBuilder prompt = new StringBuilder();
                    prompt.append(interviewerPrompt).append("\n\n");
                    prompt.append("# 面试上下文\n");
                    prompt.append("## 岗位 JD\n").append(session.getJdContent()).append("\n\n");
                    prompt.append("## 候选人简历\n").append(session.getResumeContent()).append("\n");
                    systemPrompt = prompt.toString();
                    redisMemoryService.cacheSystemPrompt(sessionId, systemPrompt);
                }
                
                // 获取历史记录
                List<String> history = redisMemoryService.getHistory(sessionId);
                
                // 不再从历史记录中提取 userMessage，因为用户消息已经在 /send 时写入 history
                // 传 null 给 generateStream，避免重复放入上下文
                String userMessage = null;
                
                // 获取面试状态
                Map<String, Object> state = interviewStateService.getState(sessionId);
                String currentStage = (String) state.get("stage");
                int stageTurnCount = (int) state.getOrDefault("stageTurnCount", 0);
                int totalTurns = (int) state.getOrDefault("totalTurns", 0);
                
                // 获取已问问题列表
                Set<Object> askedQuestionsSet = redisMemoryService.getAskedQuestions(sessionId);
                List<String> askedQuestions = new ArrayList<>();
                if (askedQuestionsSet != null) {
                    for (Object question : askedQuestionsSet) {
                        askedQuestions.add(question.toString());
                    }
                }
                
                // 构建完整的系统提示，包含面试状态信息
                StringBuilder completePrompt = new StringBuilder();
                completePrompt.append(systemPrompt).append("\n\n");
                completePrompt.append("[已问问题列表]\n");
                completePrompt.append("askedQuestions: [");
                // 只显示最近5个已问问题，避免 System Prompt 膨胀
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
                completePrompt.append("stage: " + currentStage + "\n");
                completePrompt.append("stageTurnCount: " + stageTurnCount + "\n");
                completePrompt.append("totalTurns: " + totalTurns + "\n\n");
                
                // 生成 AI 流式回复
                StringBuilder fullResponse = new StringBuilder();
                String finalSessionId = sessionId;
                String finalUserMessage = userMessage;
                String finalCurrentStage = currentStage;
                int finalStageTurnCount = stageTurnCount;
                int finalTotalTurns = totalTurns;
                
                llmService.generateStream(completePrompt.toString(), userMessage, history)
                    .subscribe(
                        chunk -> {
                            try {
                                fullResponse.append(chunk);
                                emitter.send(SseEmitter.event().data(chunk));
                                Thread.sleep(50); // 模拟打字效果
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        },
                        error -> {
                            try {
                                // 打印真实错误
                                System.err.println("[LLMService] 流式调用实际错误：" + error.getClass().getName() + " - " + error.getMessage());
                                error.printStackTrace();
                                // 显示真实错误信息
                                String errMsg = "AI 服务暂时不可用：" + error.getMessage();
                                emitter.send(SseEmitter.event().name("error").data(errMsg));
                                emitter.completeWithError(error);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        },
                        () -> {
                            try {
                                // 解析结构化输出
                                LLMService.InterviewResponse interviewResponse = llmService.parseStructuredOutput(fullResponse.toString());
                                
                                // 保存到历史记录
                                // 用户消息已在 /send 里写过，这里只写 AI 回复
                                String cleanAiResponse = stripJsonBlock(fullResponse.toString());
                                redisMemoryService.addMessage(finalSessionId, "AI：" + cleanAiResponse);
                                
                                // 添加已问问题
                                redisMemoryService.addAskedQuestion(finalSessionId, interviewResponse.getNextQuestion());
                                
                                // 更新面试状态
                                String updatedStage = finalCurrentStage;
                                int updatedStageTurnCount = finalStageTurnCount;
                                int updatedTotalTurns = finalTotalTurns;
                                
                                if (interviewResponse.isShouldMoveStage()) {
                                    updatedStage = getNextStage(finalCurrentStage);
                                    updatedStageTurnCount = 0;
                                } else {
                                    updatedStageTurnCount++;
                                }
                                updatedTotalTurns++;
                                
                                // 检查是否达到总轮次限制
                                if (updatedTotalTurns >= 20) {
                                    updatedStage = "CLOSEOUT";
                                }
                                
                                // 更新状态
                                Map<String, Object> newState = new HashMap<>();
                                newState.put("stage", updatedStage);
                                newState.put("stageTurnCount", updatedStageTurnCount);
                                newState.put("totalTurns", updatedTotalTurns);
                                interviewStateService.updateState(finalSessionId, newState);
                                
                                // 发送状态更新
                                try {
                                    ObjectMapper objectMapper = new ObjectMapper();
                                    Map<String, Object> statusUpdate = new HashMap<>();
                                    statusUpdate.put("stage", updatedStage);
                                    statusUpdate.put("stageTurnCount", updatedStageTurnCount);
                                    statusUpdate.put("totalTurns", updatedTotalTurns);
                                    statusUpdate.put("scores", interviewResponse.getScores());
                                    statusUpdate.put("questionTags", interviewResponse.getQuestionTags());
                                    String statusJson = objectMapper.writeValueAsString(statusUpdate);
                                    emitter.send(SseEmitter.event().name("status").data(statusJson));
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                                
                                // 检查是否需要结束面试
                                if (updatedStage.equals("CLOSEOUT")) {
                                    emitter.send(SseEmitter.event().name("end").data("面试结束"));
                                }
                                
                                emitter.complete();
                            } catch (Exception e) {
                                e.printStackTrace();
                                try {
                                    emitter.completeWithError(e);
                                } catch (Exception ex) {
                                    ex.printStackTrace();
                                }
                            }
                        }
                    );
                

                
            } catch (Exception e) {
                e.printStackTrace();
                try {
                    emitter.send(SseEmitter.event().name("error").data("AI 服务暂时不可用，请稍后重试"));
                    emitter.completeWithError(e);
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        }).start();
        
        return emitter;
    }

    // 面试结束评估
    @PostMapping("/evaluate")
    public Result<Map<String, Object>> evaluate(@RequestBody Map<String, String> request) {
        try {
            String sessionId = request.get("sessionId");
            
            // 获取历史记录
            List<String> history = redisMemoryService.getHistory(sessionId);
            
            // 构建评估提示
            StringBuilder prompt = new StringBuilder();
            prompt.append("# 面试评估\n");
            prompt.append("请根据以下对话历史，对候选人进行全面评估：\n\n");
            for (String msg : history) {
                prompt.append(msg).append("\n");
            }
            prompt.append("\n评估维度：\n");
            prompt.append("- 表达清晰度（1-10）\n");
            prompt.append("- 技术深度（1-10）\n");
            prompt.append("- 证据与量化（1-10）\n");
            prompt.append("- 权衡取舍意识（1-10）\n");
            prompt.append("- 复盘与改进意识（1-10）\n");
            prompt.append("\n请输出综合评分、各维度评分、亮点列表、薄弱点列表和建议提升方向。");
            
            // 生成评估报告
            String systemPrompt = llmService.loadSystemPrompt();
            String response = llmService.generateResponse(systemPrompt, prompt.toString(), new ArrayList<>());
            
            // 清理会话数据
            redisMemoryService.cleanSession(sessionId);
            
            Map<String, Object> result = new HashMap<>();
            result.put("report", response);
            return Result.success(result);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("评估失败：" + e.getMessage());
        }
    }
    
    private String stripJsonBlock(String text) {
        if (text == null || text.isBlank()) return text;
        int lastBrace = text.lastIndexOf('{');
        if (lastBrace == -1) return text;
        String possibleJson = text.substring(lastBrace);
        if (possibleJson.contains("\"feedback\"") &&
            possibleJson.contains("\"next_question\"")) {
            return text.substring(0, lastBrace).trim();
        }
        return text;
    }
}
