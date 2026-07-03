package com.example.aiinterview.service.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;

/**
 * 兼容 OpenAI 协议的大模型客户端（DeepSeek / 火山引擎 Ark / 阿里云百炼等均支持）
 * 通过构造参数传入不同的 apiKey / baseUrl / model 来适配不同供应商
 */
public class OpenAIChatClient implements ChatClient {

    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public OpenAIChatClient(String apiKey, String baseUrl, String model) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException(
                "API Key 未配置，请设置对应环境变量（DeepSeek: DEEPSEEK_API_KEY, Ark: ARK_API_KEY）"
            );
        }
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
        this.restClient = RestClient.builder().build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String chat(List<Map<String, String>> messages) {
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("messages", messages);

            String response = restClient.post()
                    .uri(baseUrl + "/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            return extractContent(response);

        } catch (Exception e) {
            throw new RuntimeException("LLM 调用失败 (" + model + "): " + e.getMessage(), e);
        }
    }

    @Override
    public void stream(List<Map<String, String>> messages, BlockingQueue<String> queue) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", messages);
        requestBody.put("stream", true);

        java.net.HttpURLConnection conn = null;
        try {
            java.net.URL url = new java.net.URL(baseUrl + "/chat/completions");
            conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "text/event-stream");
            conn.setDoOutput(true);
            conn.setReadTimeout(0);

            try (java.io.OutputStream os = conn.getOutputStream()) {
                os.write(objectMapper.writeValueAsBytes(requestBody));
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("data: ")) continue;
                    String data = line.substring(6).trim();
                    if ("[DONE]".equals(data)) break;
                    @SuppressWarnings("unchecked")
                    Map<String, Object> jsonMap = objectMapper.readValue(data, Map.class);
                    List<Map<String, Object>> choices =
                            (List<Map<String, Object>>) jsonMap.get("choices");
                    if (choices != null && !choices.isEmpty()) {
                        Map<String, Object> delta =
                                (Map<String, Object>) choices.get(0).get("delta");
                        if (delta != null && delta.get("content") != null) {
                            queue.put(delta.get("content").toString());
                        }
                    }
                }
            } catch (Exception e) {
                queue.put("__ERROR__:" + e.getMessage());
            }
        } catch (Exception e) {
            try { queue.put("__ERROR__:" + e.getMessage()); } catch (Exception ignored) {}
        } finally {
            try { queue.put("__DONE__"); } catch (Exception ignored) {}
            if (conn != null) conn.disconnect();
        }
    }

    private String extractContent(String responseJson) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = objectMapper.readValue(responseJson, Map.class);
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            if (choices != null && !choices.isEmpty()) {
                Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                if (message != null) {
                    return (String) message.get("content");
                }
            }
        } catch (Exception e) {
            System.err.println("[OpenAIChatClient] 解析API响应失败: " + e.getMessage());
        }
        return "";
    }
}
