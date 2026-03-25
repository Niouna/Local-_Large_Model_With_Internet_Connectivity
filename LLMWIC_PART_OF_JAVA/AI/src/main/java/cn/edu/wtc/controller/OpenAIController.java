package cn.edu.wtc.controller;

import cn.edu.wtc.dto.ChatCompletionRequest;
import cn.edu.wtc.dto.ChatCompletionResponse;
import cn.edu.wtc.ollama.service.SessionManager;
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
    private final SessionManager sessionManager;

    @Autowired
    public OpenAIController(RAGOrchestrator ragOrchestrator, SessionManager sessionManager) {
        this.ragOrchestrator = ragOrchestrator;
        this.sessionManager = sessionManager;
    }

    @PostMapping("/chat/completions")
    public ChatCompletionResponse chatCompletions(@RequestBody ChatCompletionRequest request) {
        String userMessage = request.getMessages().stream()
                .reduce((first, second) -> second)
                .map(ChatCompletionRequest.Message::getContent)
                .orElse("");
        String model = request.getModel();

        // 1. 获取前端传来的 sessionId (可能为 null)
        String sessionId = request.getSessionId();

        // 2. 新增：获取前端传来的 isWebSearchEnabled 开关状态 (可能为 null, 默认为 false)
        boolean isWebSearchEnabled = request.isWebSearchEnabled() != null && request.isWebSearchEnabled();

        String aiResponse;
        String actualSessionId;
        try {
            // 3. 【核心逻辑】调用 RAGOrchestrator，传入用户设定的 isWebSearchEnabled 状态
            aiResponse = ragOrchestrator.chatWithRag(sessionId, model, userMessage, isWebSearchEnabled);
            actualSessionId = sessionManager.getCurrentSessionId();
        } catch (IOException e) {
            throw new RuntimeException("AI 调用失败: " + e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("参数错误: " + e.getMessage(), e);
        }

        ChatCompletionResponse response = new ChatCompletionResponse();
        response.setId("chatcmpl-" + UUID.randomUUID());
        response.setCreated(System.currentTimeMillis() / 1000);
        response.setModel(model);
        response.setObject("chat.completion");

        ChatCompletionResponse.Choice choice = new ChatCompletionResponse.Choice();
        choice.setIndex(0);
        ChatCompletionResponse.Message msg = new ChatCompletionResponse.Message();
        msg.setRole("assistant");
        msg.setContent(aiResponse);
        choice.setMessage(msg);
        choice.setFinishReason("stop");
        response.setChoices(List.of(choice));

        if (actualSessionId != null) {
            response.setSessionId(actualSessionId);
        }

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