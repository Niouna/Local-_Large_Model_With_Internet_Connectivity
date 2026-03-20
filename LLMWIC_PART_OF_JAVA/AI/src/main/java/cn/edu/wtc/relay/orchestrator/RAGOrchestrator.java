package cn.edu.wtc.relay.orchestrator; // 替换为你的实际包名

import cn.edu.wtc.memory.service.MemoryService;
import cn.edu.wtc.ollama.service.ChatService;
import cn.edu.wtc.ollama.service.SessionManager;
import cn.edu.wtc.relay.detector.IntentDetector;
import cn.edu.wtc.relay.search.SearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import cn.edu.wtc.ollama.model.Conversation;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

@Component
public class RAGOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(RAGOrchestrator.class);

    @Autowired
    private ChatService chatService;
    @Autowired
    private SearchService searchService;
    @Autowired
    private MemoryService memoryService;
    @Autowired
    private SessionManager sessionManager;
    // 在类中注入 IntentDetector
    @Autowired
    private IntentDetector intentDetector;

    /**
     * 带 Session 的对话入口 (支持记忆和联网)
     */
    public String chatWithRag(String sessionId, String model, String message) throws IOException {
        log.info("收到原始请求 -> SessionId: [{}], Model: [{}], Message: {}", sessionId, model, message);

        if (model == null || model.trim().isEmpty()) {
            log.warn("⚠️ 检测到模型名称为空，强制使用默认模型 deepseek-r1:7b");
            model = "deepseek-r1:7b";
        }
        String actualModel = model.trim();

        Conversation conversation = sessionManager.getOrCreateSession(sessionId, actualModel);
        String effectiveSessionId = conversation.getSessionId();
        log.info("✅ 会话已就绪 -> 有效 SessionId: [{}], 模型：{}", effectiveSessionId, actualModel);

        // 获取历史记忆上下文（只需一次）
        String memoryContext = memoryService.assembleMemoryContext(effectiveSessionId);

        // 判断是否需要联网搜索
        boolean needWebSearch = needsWebSearch(message);
        String searchResult = null;
        String realQuery = message; // 默认使用原始消息

        if (needWebSearch) {
            // 提取真正的查询词（去掉“联网搜索”等触发词）
            realQuery = intentDetector.extractQuery(message);
            log.info("提取的查询词: {}", realQuery);
            searchResult = performWebSearch(realQuery);
        }

        String finalPrompt;
        if (needWebSearch) {
            String searchPart = String.format("基于以下搜索结果回答问题。\n搜索结果：%s\n\n用户问题：%s", searchResult, realQuery);
            if (!memoryContext.isEmpty()) {
                finalPrompt = "以下是历史信息（请参考这些信息回答用户的问题）：\n" + memoryContext + "\n\n" + searchPart;
            } else {
                finalPrompt = searchPart;
            }
        } else {
            if (!memoryContext.isEmpty()) {
                finalPrompt = "以下是历史信息（请参考这些信息回答用户的问题）：\n" + memoryContext + "\n用户问题：" + message;
            } else {
                finalPrompt = message;
            }
        }

        log.info("最终 Prompt（前500字符）:\n{}", finalPrompt.length() > 500 ? finalPrompt.substring(0, 500) + "..." : finalPrompt);

        // 调用 ChatService（传入 finalPrompt，它已经包含了正确的用户问题）
        String answer = chatService.chatWithSession(effectiveSessionId, actualModel, finalPrompt);

        // 异步写入记忆：使用真实用户消息（realQuery 或原始 message）
        final String finalSessionId = effectiveSessionId;
        final String finalAnswer = answer;
        final String finalUserMsg = needWebSearch ? realQuery : message; // 关键修改

        CompletableFuture.runAsync(() -> {
            try {
                memoryService.addL1Memory(finalSessionId, finalUserMsg, finalAnswer);
                log.debug("记忆已异步写入会话：{}", finalSessionId);
            } catch (Exception e) {
                log.error("异步记忆写入失败", e);
            }
        });

        return answer;
    }

    public String chatWithRag(String model, String message) throws IOException {
        return chatWithRag(null, model, message);
    }

    private boolean needsWebSearch(String query) {
        String prompt = "判断以下问题是否需要联网搜索最新信息？只需回答'是'或'否'。问题：" + query;
        try {
            String res = chatService.chat("qwen2.5:3b", prompt);
            return res != null && res.contains("是");
        } catch (Exception e) {
            return false;
        }
    }

    private String performWebSearch(String query) {
        try {
            return searchService.search(query, 5);
        } catch (Exception e) {
            log.error("联网搜索失败", e);
            return "搜索服务暂时不可用，请稍后重试。";
        }
    }
}