package com.example.aiinterview.config;

import com.example.aiinterview.service.client.ChatClient;
import com.example.aiinterview.service.client.OpenAIChatClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * LLM 客户端配置 — 根据 llm.provider 创建对应的 ChatClient Bean
 *
 * 切换方式（三选一）：
 *   1. 环境变量: set LLM_PROVIDER=deepseek
 *   2. 启动参数: --llm.provider=ark
 *   3. application.yml: llm.provider: ark
 */
@Configuration
public class ChatClientConfig {

    private static final Logger log = LoggerFactory.getLogger(ChatClientConfig.class);

    @Bean
    @ConditionalOnProperty(name = "llm.provider", havingValue = "deepseek", matchIfMissing = true)
    public ChatClient deepSeekClient(@Value("${DEEPSEEK_API_KEY:}") String apiKey) {
        log.info("初始化 DeepSeek ChatClient, key长度: {}", apiKey != null ? apiKey.length() : 0);
        if (apiKey == null || apiKey.isBlank()) {
            log.error("DEEPSEEK_API_KEY 为空，请检查 IDEA Run Configuration 中的 Environment variables 配置");
            // 临时方案：直接把你的 DeepSeek Key 填下面这行（先跑起来，之后再改回环境变量）
            // apiKey = "sk-你的key";
        }
        return new OpenAIChatClient(apiKey, "https://api.deepseek.com", "deepseek-chat");
    }

    @Bean
    @ConditionalOnProperty(name = "llm.provider", havingValue = "ark")
    public ChatClient arkClient(@Value("${ARK_API_KEY:}") String apiKey) {
        log.info("初始化 火山引擎 Ark ChatClient");
        return new OpenAIChatClient(apiKey, "https://ark.cn-beijing.volces.com/api/coding/v3", "doubao-seed-2.0-pro");
    }

    @Bean
    @ConditionalOnProperty(name = "llm.provider", havingValue = "ollama")
    public ChatClient ollamaClient() {
        log.info("初始化 Ollama ChatClient（本地）");
        // Ollama 本地不需要 API Key
        return new OpenAIChatClient("ollama", "http://localhost:11434/v1", "qwen2.5:7b");
    }
}
