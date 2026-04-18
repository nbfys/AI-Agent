package com.example.aiinterview.service;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
public class OllamaTest {

    @Autowired
    private ChatClient.Builder chatClientBuilder;

    @Test
    public void testOllamaChat() {
        // 创建 ChatClient
        ChatClient chatClient = chatClientBuilder.build();
        
        // 测试本地 Ollama 模型
        SystemMessage systemMessage = new SystemMessage("你是一位资深技术面试官，专注于 Java 后端与 AI 工程方向。");
        UserMessage userMessage = new UserMessage("请介绍一下 Spring Boot 的核心特性。");

        Prompt prompt = new Prompt(List.of(systemMessage, userMessage));
        String response = chatClient.prompt(prompt).call().content();

        System.out.println("Ollama 模型回复:");
        System.out.println(response);

        assertNotNull(response, "回复不能为空");
        assertTrue(response.length() > 0, "回复长度必须大于 0");
    }
}
