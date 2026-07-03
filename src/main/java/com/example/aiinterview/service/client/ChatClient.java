package com.example.aiinterview.service.client;

import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;

/**
 * LLM 调用客户端接口
 * 所有 AI 模型提供商实现此接口，LLMService 面向接口编程，无需关心具体实现
 */
public interface ChatClient {

    /**
     * 同步调用
     * @param messages 已组装好的消息列表（含 system + history + user）
     * @return 模型返回的文本内容
     */
    String chat(List<Map<String, String>> messages);

    /**
     * 流式调用（SSE）
     * @param messages 已组装好的消息列表
     * @param queue    输出队列，每段文本入队，末尾入队 __DONE__，出错入队 __ERROR__:xxx
     */
    void stream(List<Map<String, String>> messages, BlockingQueue<String> queue);
}
