package cn.edu.wtc.controller;

import cn.edu.wtc.dto.ChatCompletionRequest;
import cn.edu.wtc.dto.ChatCompletionResponse;
import cn.edu.wtc.ollama.service.SessionManager; // 1. 引入 SessionManager
import cn.edu.wtc.relay.orchestrator.RAGOrchestrator;
import cn.edu.wtc.util.TokenEstimator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1")
public class OpenAIController {

    private final RAGOrchestrator ragOrchestrator;
    private final SessionManager sessionManager; // 2. 声明 SessionManager

    @Autowired
    public OpenAIController(RAGOrchestrator ragOrchestrator, SessionManager sessionManager) {
        this.ragOrchestrator = ragOrchestrator;
        this.sessionManager = sessionManager; // 3. 注入 SessionManager
    }

    @PostMapping("/chat/completions")
    public ChatCompletionResponse chatCompletions(@RequestBody ChatCompletionRequest request) {
        // 提取用户最新消息
        String userMessage = request.getMessages().stream()
                .reduce((first, second) -> second)
                .map(ChatCompletionRequest.Message::getContent)
                .orElse("");

        String model = request.getModel();
        // 4. 获取前端传来的 sessionId (可能为 null)
        String sessionId = request.getSessionId();

        String aiResponse;
        String actualSessionId;

        try {
            // 【核心逻辑】
            // 调用 RAGOrchestrator，传入 sessionId。
            // 注意：如果 RAGOrchestrator 还没改，请看下面的【重要提示】部分。
            aiResponse = ragOrchestrator.chatWithRag(sessionId, model, userMessage);

            // 【关键】从 SessionManager 获取实际生效的 ID (如果是自动生成的，这里能拿到)
            actualSessionId = sessionManager.getCurrentSessionId();

        } catch (IOException e) {
            throw new RuntimeException("AI 调用失败: " + e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("参数错误: " + e.getMessage(), e);
        }

        // 构建响应对象
        ChatCompletionResponse response = new ChatCompletionResponse();
        response.setId("chatcmpl-" + UUID.randomUUID());
        response.setCreated(System.currentTimeMillis() / 1000);
        response.setModel(model);
        response.setObject("chat.completion");

        // 设置回复内容
        ChatCompletionResponse.Choice choice = new ChatCompletionResponse.Choice();
        choice.setIndex(0);
        ChatCompletionResponse.Message msg = new ChatCompletionResponse.Message();
        msg.setRole("assistant");
        msg.setContent(aiResponse);
        choice.setMessage(msg);
        choice.setFinishReason("stop");
        response.setChoices(List.of(choice));

        // 【关键修复】将 sessionId 回填到响应体中
        // 这样前端下次请求时就能带上这个 ID，实现会话连续
        if (actualSessionId != null) {
            response.setSessionId(actualSessionId);

        }

        // 计算 Token (估算)
        ChatCompletionResponse.Usage usage = new ChatCompletionResponse.Usage();
        int promptTokens = TokenEstimator.estimateTokens(userMessage);
        int completionTokens = TokenEstimator.estimateTokens(aiResponse);
        usage.setPromptTokens(promptTokens);
        usage.setCompletionTokens(completionTokens);
        usage.setTotalTokens(promptTokens + completionTokens);
        response.setUsage(usage);

        return response;
    }
}