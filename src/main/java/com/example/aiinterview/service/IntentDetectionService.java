package com.example.aiinterview.service;

import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

@Service
public class IntentDetectionService {

    @Resource
    private LLMService llmService;

    /**
     * 意图类型枚举
     */
    public enum IntentType {
        VALID_TECHNICAL_QUESTION,   // 有效技术问题
        REASONABLE_FOLLOWUP,        // 合理追问
        COMPENSATION_QUESTION,      // 薪资问题
        IRRELEVANT_QUESTION,        // 无关问题
        OFF_TOPIC,                  // 完全跑题
        SYSTEM_ISSUE,               // 系统问题
        END_INTERVIEW               // 结束面试
    }

    /**
     * 意图检测结果
     */
    public static class IntentResult {
        private IntentType intentType;
        private String analysis;
        private boolean shouldContinueStage;  // 是否继续当前阶段
        private String suggestedResponse;     // 建议的回应

        public IntentResult(IntentType intentType, String analysis, boolean shouldContinueStage, String suggestedResponse) {
            this.intentType = intentType;
            this.analysis = analysis;
            this.shouldContinueStage = shouldContinueStage;
            this.suggestedResponse = suggestedResponse;
        }

        public IntentType getIntentType() {
            return intentType;
        }

        public String getAnalysis() {
            return analysis;
        }

        public boolean isShouldContinueStage() {
            return shouldContinueStage;
        }

        public String getSuggestedResponse() {
            return suggestedResponse;
        }
    }

    /**
     * 检测用户意图
     */
    public IntentResult detectIntent(String userInput, String currentStage) {
        String prompt = buildIntentDetectionPrompt(userInput, currentStage);
        
        // 调用LLM进行意图检测
        String result = llmService.generateResponse(prompt, "", new java.util.ArrayList<>());
        
        // 解析意图检测结果
        return parseIntentResult(result);
    }

    /**
     * 构建意图检测提示词
     */
    private String buildIntentDetectionPrompt(String userInput, String currentStage) {
        return """
            你是一个专业的意图检测助手。请分析用户输入的意图，并返回JSON格式的结果。
            
            当前面试阶段：%s
            
            用户输入：%s
            
            请分析用户的意图，从以下类型中选择最合适的：
            - VALID_TECHNICAL_QUESTION: 有效技术问题，与当前阶段相关
            - REASONABLE_FOLLOWUP: 合理追问，是对之前问题的澄清
            - COMPENSATION_QUESTION: 薪资、福利等非技术问题
            - IRRELEVANT_QUESTION: 与面试无关但可能需要礼貌回应
            - OFF_TOPIC: 完全跑题，需要引导回面试
            - SYSTEM_ISSUE: 系统问题或功能反馈
            - END_INTERVIEW: 用户想要结束面试
            
            【重要提示】
            1. 对于自我介绍（如"我叫xxx"、"我来自xxx"、"我学的是xxx专业"），一定标记为VALID_TECHNICAL_QUESTION，不是IRRELEVANT！
            2. 在OPENING阶段，用户的自我介绍、简历背景介绍等都是正常内容，不应被误判为无关。
            3. 关于结束判断：
               - 明确说"结束"、"结束面试"、"不面了" → 标记为END_INTERVIEW
               - 在面试官问"还有补充吗"后，用户说"没了"、"没有了"、"就这些" → 标记为REASONABLE_FOLLOWUP（提示面试官换话题）
            4. 对于"我参与了xxx项目"、"我会xxx技术"等，都是VALID_TECHNICAL_QUESTION。
            5. 对于"好的"、"嗯"、"是的"等简短回答 → 标记为REASONABLE_FOLLOWUP。
            
            返回JSON格式：
            {
                "intentType": "意图类型",
                "analysis": "简要分析",
                "shouldContinueStage": true/false, // 是否继续当前阶段不跳转
                "suggestedResponse": "建议的回应"
            }
            
            注意：
            - 对于薪资问题，建议回应应该委婉但明确
            - 对于无关问题，建议回应应该引导回面试主题
            - 只有当用户明确表示要结束时才标记为END_INTERVIEW
            - 自我介绍绝对不能标记为IRRELEVANT！
            """.formatted(currentStage, userInput);
    }

    /**
     * 解析意图检测结果
     */
    private IntentResult parseIntentResult(String llmResponse) {
        try {
            // 简化解析（实际项目中应该使用JSON解析库）
            Map<String, String> parsed = new HashMap<>();
            
            // 提取intentType
            if (llmResponse.contains("\"intentType\":")) {
                String type = extractField(llmResponse, "intentType");
                IntentType intentType = IntentType.valueOf(type);
                
                String analysis = extractField(llmResponse, "analysis");
                boolean shouldContinueStage = Boolean.parseBoolean(extractField(llmResponse, "shouldContinueStage"));
                String suggestedResponse = extractField(llmResponse, "suggestedResponse");
                
                return new IntentResult(intentType, analysis, shouldContinueStage, suggestedResponse);
            }
        } catch (Exception e) {
            // 解析失败时返回默认值
        }
        
        // 默认认为是有效技术问题
        return new IntentResult(
            IntentType.VALID_TECHNICAL_QUESTION,
            "默认判定为有效技术问题",
            true,
            null
        );
    }

    /**
     * 简单的字段提取方法
     */
    private String extractField(String response, String fieldName) {
        String searchStr = "\"" + fieldName + "\":";
        int start = response.indexOf(searchStr);
        if (start == -1) return "";
        
        start += searchStr.length();
        boolean inQuotes = false;
        StringBuilder result = new StringBuilder();
        
        for (int i = start; i < response.length(); i++) {
            char c = response.charAt(i);
            if (c == '\"') {
                inQuotes = !inQuotes;
                if (!inQuotes) break;
                continue;
            }
            if (inQuotes) {
                result.append(c);
            } else if (c == ',' || c == '}' || c == '\n') {
                break;
            } else if (c != ' ' && c != '\t') {
                result.append(c);
            }
        }
        
        return result.toString().trim();
    }
}
