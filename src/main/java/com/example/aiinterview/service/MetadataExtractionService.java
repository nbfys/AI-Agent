package com.example.aiinterview.service;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

@Service
public class MetadataExtractionService {

    @Resource
    private LLMService llmService;

    @Resource
    private RedisMemoryService redisMemoryService;

    @Resource
    private CandidateProfileService candidateProfileService;

    /**
     * 候选人元数据结构
     */
    public static class CandidateProfile {
        private String technicalStack;      // 技术栈
        private String keyProjects;         // 关键项目
        private String strengths;           // 优势
        private String weaknesses;          // 薄弱点
        private String careerGoals;         // 职业目标
        private String painPoints;          // 痛点
        private String workExperience;      // 工作经验
        private String education;           // 教育背景

        public CandidateProfile() {
            this.technicalStack = "";
            this.keyProjects = "";
            this.strengths = "";
            this.weaknesses = "";
            this.careerGoals = "";
            this.painPoints = "";
            this.workExperience = "";
            this.education = "";
        }

        // Getters and Setters
        public String getTechnicalStack() {
            return technicalStack;
        }

        public void setTechnicalStack(String technicalStack) {
            this.technicalStack = technicalStack;
        }

        public String getKeyProjects() {
            return keyProjects;
        }

        public void setKeyProjects(String keyProjects) {
            this.keyProjects = keyProjects;
        }

        public String getStrengths() {
            return strengths;
        }

        public void setStrengths(String strengths) {
            this.strengths = strengths;
        }

        public String getWeaknesses() {
            return weaknesses;
        }

        public void setWeaknesses(String weaknesses) {
            this.weaknesses = weaknesses;
        }

        public String getCareerGoals() {
            return careerGoals;
        }

        public void setCareerGoals(String careerGoals) {
            this.careerGoals = careerGoals;
        }

        public String getPainPoints() {
            return painPoints;
        }

        public void setPainPoints(String painPoints) {
            this.painPoints = painPoints;
        }

        public String getWorkExperience() {
            return workExperience;
        }

        public void setWorkExperience(String workExperience) {
            this.workExperience = workExperience;
        }

        public String getEducation() {
            return education;
        }

        public void setEducation(String education) {
            this.education = education;
        }

        /**
         * 转换为字符串用于Prompt
         */
        public String toPromptString() {
            StringBuilder sb = new StringBuilder();
            sb.append("## 候选人核心信息\n");
            if (technicalStack != null && !technicalStack.isEmpty()) {
                sb.append("- 技术栈：").append(technicalStack).append("\n");
            }
            if (keyProjects != null && !keyProjects.isEmpty()) {
                sb.append("- 关键项目：").append(keyProjects).append("\n");
            }
            if (strengths != null && !strengths.isEmpty()) {
                sb.append("- 优势：").append(strengths).append("\n");
            }
            if (weaknesses != null && !weaknesses.isEmpty()) {
                sb.append("- 薄弱点：").append(weaknesses).append("\n");
            }
            if (careerGoals != null && !careerGoals.isEmpty()) {
                sb.append("- 职业目标：").append(careerGoals).append("\n");
            }
            if (painPoints != null && !painPoints.isEmpty()) {
                sb.append("- 痛点：").append(painPoints).append("\n");
            }
            if (workExperience != null && !workExperience.isEmpty()) {
                sb.append("- 工作经验：").append(workExperience).append("\n");
            }
            if (education != null && !education.isEmpty()) {
                sb.append("- 教育背景：").append(education).append("\n");
            }
            return sb.toString();
        }
    }

    /**
     * 异步提取元数据
     */
    @Async
    public void extractAndUpdateMetadata(String sessionId, String latestUserMessage, String latestAiResponse) {
        try {
            // 获取当前的候选人画像
            CandidateProfile currentProfile = getCandidateProfile(sessionId);
            
            // 提取新信息
            String extractionPrompt = buildExtractionPrompt(latestUserMessage, latestAiResponse, currentProfile);
            String extractionResult = llmService.generateResponse(extractionPrompt, "", new java.util.ArrayList<>());
            
            // 更新候选人画像
            CandidateProfile updatedProfile = mergeMetadata(currentProfile, extractionResult);
            
            // 保存到Redis
            redisMemoryService.saveCandidateProfile(sessionId, updatedProfile);

            // 持久化到MySQL
            candidateProfileService.saveOrUpdate(sessionId,
                    updatedProfile.getTechnicalStack(),
                    updatedProfile.getKeyProjects(),
                    updatedProfile.getStrengths(),
                    updatedProfile.getWeaknesses(),
                    updatedProfile.getCareerGoals(),
                    updatedProfile.getPainPoints(),
                    updatedProfile.getWorkExperience(),
                    updatedProfile.getEducation());
            
            System.out.println("[MetadataExtraction] 元数据提取完成：" + sessionId);
        } catch (Exception e) {
            System.err.println("[MetadataExtraction] 元数据提取失败：" + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 构建提取提示词
     */
    private String buildExtractionPrompt(String userMessage, String aiResponse, CandidateProfile currentProfile) {
        return """
            你是一个专业的元数据提取助手。请从以下对话中提取候选人的关键信息。
            
            当前已有信息：
            %s
            
            最新对话：
            用户：%s
            AI：%s
            
            请提取可能更新的信息，并返回JSON格式：
            {
                "technicalStack": "技术栈（如：Java, Spring, Redis）",
                "keyProjects": "关键项目",
                "strengths": "优势",
                "weaknesses": "薄弱点",
                "careerGoals": "职业目标",
                "painPoints": "痛点",
                "workExperience": "工作经验",
                "education": "教育背景"
            }
            
            注意：
            - 只提取有明确提及的信息
            - 对于没有提及的字段，保持为空字符串
            - 信息要简洁明了，避免冗余
            """.formatted(currentProfile.toPromptString(), userMessage, aiResponse);
    }

    /**
     * 合并元数据
     */
    private CandidateProfile mergeMetadata(CandidateProfile current, String extractionResult) {
        CandidateProfile merged = new CandidateProfile();
        
        // 保留原有信息
        merged.setTechnicalStack(current.getTechnicalStack());
        merged.setKeyProjects(current.getKeyProjects());
        merged.setStrengths(current.getStrengths());
        merged.setWeaknesses(current.getWeaknesses());
        merged.setCareerGoals(current.getCareerGoals());
        merged.setPainPoints(current.getPainPoints());
        merged.setWorkExperience(current.getWorkExperience());
        merged.setEducation(current.getEducation());
        
        // 尝试从提取结果中更新信息
        try {
            Map<String, String> newInfo = parseExtractionResult(extractionResult);
            
            // 更新技术栈（累加）
            if (newInfo.containsKey("technicalStack") && !newInfo.get("technicalStack").isEmpty()) {
                String newStack = newInfo.get("technicalStack");
                if (!merged.getTechnicalStack().contains(newStack)) {
                    merged.setTechnicalStack(merged.getTechnicalStack().isEmpty() ? 
                        newStack : merged.getTechnicalStack() + ", " + newStack);
                }
            }
            
            // 更新其他字段（只更新非空的）
            updateIfNotEmpty(merged::setKeyProjects, newInfo, "keyProjects");
            updateIfNotEmpty(merged::setStrengths, newInfo, "strengths");
            updateIfNotEmpty(merged::setWeaknesses, newInfo, "weaknesses");
            updateIfNotEmpty(merged::setCareerGoals, newInfo, "careerGoals");
            updateIfNotEmpty(merged::setPainPoints, newInfo, "painPoints");
            updateIfNotEmpty(merged::setWorkExperience, newInfo, "workExperience");
            updateIfNotEmpty(merged::setEducation, newInfo, "education");
            
        } catch (Exception e) {
            // 解析失败时保留原有信息
        }
        
        return merged;
    }

    /**
     * 辅助方法：非空则更新
     */
    private void updateIfNotEmpty(java.util.function.Consumer<String> setter, Map<String, String> info, String key) {
        if (info.containsKey(key) && !info.get(key).isEmpty()) {
            setter.accept(info.get(key));
        }
    }

    /**
     * 解析提取结果
     */
    private Map<String, String> parseExtractionResult(String llmResponse) {
        Map<String, String> result = new HashMap<>();
        String[] fields = {"technicalStack", "keyProjects", "strengths", "weaknesses", 
                          "careerGoals", "painPoints", "workExperience", "education"};
        
        for (String field : fields) {
            String searchStr = "\"" + field + "\":";
            int start = llmResponse.indexOf(searchStr);
            if (start != -1) {
                int valueStart = llmResponse.indexOf("\"", start + searchStr.length()) + 1;
                int valueEnd = llmResponse.indexOf("\"", valueStart);
                if (valueEnd != -1) {
                    result.put(field, llmResponse.substring(valueStart, valueEnd).trim());
                }
            }
        }
        
        return result;
    }

    /**
     * 获取候选人画像
     */
    public CandidateProfile getCandidateProfile(String sessionId) {
        CandidateProfile profile = redisMemoryService.getCandidateProfile(sessionId);
        return profile != null ? profile : new CandidateProfile();
    }
}
