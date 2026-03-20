package cn.edu.wtc.ollama.service;

import cn.edu.wtc.ollama.client.OllamaClient;
import cn.edu.wtc.ollama.model.Conversation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@Service
public class ChatService {
    private final OllamaClient ollamaClient;
    private final SessionManager sessionManager;

    @Autowired
    public ChatService(OllamaClient ollamaClient, SessionManager sessionManager) {
        this.ollamaClient = ollamaClient;
        this.sessionManager = sessionManager;
    }

    /**
     * 单次对话（无历史）
     */
    public String chat(String model, String message) throws IOException {
        List<Map<String, String>> messages = List.of(Map.of("role", "user", "content", message));
        return ollamaClient.chat(model, messages);
    }

    /**
     * 连续对话（带会话历史）
     */
    public String chatWithSession(String sessionId, String model, String message) throws IOException {
        Conversation conv = sessionManager.getOrCreateSession(sessionId, model);
        conv.addUserMessage(message);

        List<Map<String, String>> history = conv.getHistoryForApi();
        String response = ollamaClient.chat(conv.getModel(), history);

        conv.addAssistantMessage(response);
        return response;
    }

    public String chatWithSession(String sessionId, String message) throws IOException {
        Conversation conv = sessionManager.getSession(sessionId);
        if (conv == null) {
            throw new IOException("会话不存在: " + sessionId);
        }
        return chatWithSession(sessionId, conv.getModel(), message);
    }

    public Conversation getSession(String sessionId) {
        return sessionManager.getSession(sessionId);
    }

    public void endSession(String sessionId) {
        sessionManager.endSession(sessionId);
    }

    public void listSessions() {
        sessionManager.listSessions();
    }

    public boolean healthCheck() {
        return ollamaClient.healthCheck();
    }

    public String listModels() throws IOException {
        return ollamaClient.listModels();
    }

    public String generate(String model, String prompt) throws IOException {
        return ollamaClient.generate(model, prompt);
    }
}