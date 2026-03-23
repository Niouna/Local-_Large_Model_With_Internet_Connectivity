package cn.edu.wtc.relay.orchestrator;

import cn.edu.wtc.memory.service.MemoryService;
import cn.edu.wtc.ollama.service.ChatService;
import cn.edu.wtc.ollama.service.SessionManager;
import cn.edu.wtc.relay.detector.IntentDetector;
import cn.edu.wtc.relay.search.SearchService;
// <--- 新增导入
import cn.edu.wtc.memory.entity.RagRequestLog;
import cn.edu.wtc.memory.repository.RagRequestLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import cn.edu.wtc.ollama.model.Conversation;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
    @Autowired
    private IntentDetector intentDetector;

    // <--- 新增注入
    @Autowired
    private RagRequestLogRepository ragLogRepository;

    /**
     * 带 Session 的对话入口 (支持记忆和联网)
     */
    public String chatWithRag(String sessionId, String model, String message) throws IOException {
        long startTime = System.currentTimeMillis(); // <--- 记录开始时间

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

        String finalPromptText; // <--- 重命名变量，避免与局部变量冲突
        if (needWebSearch) {
            String searchPart = String.format("基于以下搜索结果回答问题。\n搜索结果：%s\n\n用户问题：%s", searchResult, realQuery);
            if (!memoryContext.isEmpty()) {
                finalPromptText = "以下是历史信息（请参考这些信息回答用户的问题）：\n" + memoryContext + "\n\n" + searchPart;
            } else {
                finalPromptText = searchPart;
            }
        } else {
            if (!memoryContext.isEmpty()) {
                finalPromptText = "以下是历史信息（请参考这些信息回答用户的问题）：\n" + memoryContext + "\n用户问题：" + message;
            } else {
                finalPromptText = message;
            }
        }

        log.info("最终 Prompt（前500字符）:\n{}", finalPromptText.length() > 500 ? finalPromptText.substring(0, 500) + "..." : finalPromptText);

        // 调用 ChatService
        String answer = chatService.chatWithSession(effectiveSessionId, actualModel, finalPromptText);

        long endTime = System.currentTimeMillis(); // <--- 记录结束时间

        // --- 原有异步记忆逻辑 ---
        final String finalSessionId = effectiveSessionId;
        final String finalAnswer = answer;
        final String finalUserMsg = needWebSearch ? realQuery : message;

        CompletableFuture.runAsync(() -> {
            try {
                memoryService.addL1Memory(finalSessionId, finalUserMsg, finalAnswer);
                log.debug("记忆已异步写入会话：{}", finalSessionId);
            } catch (Exception e) {
                log.error("异步记忆写入失败", e);
            }
        });

        // --- 新增：异步记录 RAG 请求日志 ---
        // 捕获最终变量用于 Lambda
        final String logSessionId = effectiveSessionId;
        final String logUserMsg = message; // 记录用户原始输入
        final boolean logNeedWeb = needWebSearch;
        final String logRealQuery = realQuery;
        final String logSearchResult = searchResult != null ? searchResult : "";
        final String logMemoryContext = memoryContext != null ? memoryContext : "";
        final String logFinalPrompt = finalPromptText;
        final String logAiResponse = answer;
        final int logProcessTime = (int)(endTime - startTime);

        CompletableFuture.runAsync(() -> {
            try {
                RagRequestLog logEntry = new RagRequestLog();
                logEntry.setSessionId(logSessionId);
                // requestTime 和 createdAt 会在 @PrePersist 中自动设置，也可以手动设
                logEntry.setRequestTime(LocalDateTime.now());
                logEntry.setUserMessage(logUserMsg);
                logEntry.setNeedWebSearch(logNeedWeb);
                logEntry.setRealQuery(logRealQuery);
                logEntry.setSearchResult(logSearchResult);
                logEntry.setMemoryContext(logMemoryContext);
                logEntry.setFinalPrompt(logFinalPrompt);
                logEntry.setAiResponse(logAiResponse);
                logEntry.setProcessTimeMs(logProcessTime);

                ragLogRepository.save(logEntry);
                log.debug("RAG 请求日志已异步保存，耗时: {} ms", logProcessTime);
            } catch (Exception e) {
                log.error("异步保存 RAG 日志失败", e);
            }
        });

        return answer;
    }

    public String chatWithRag(String model, String message) throws IOException {
        return chatWithRag(null, model, message);
    }

    private boolean needsWebSearch(String query) {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String currentTime = now.format(formatter);

        String prompt = String.format(
                "当前时间是：%s。判断以下问题是否需要获取最新的实时信息或近期事件。" +
                        "如果问题涉及新闻、事件、实时数据、需要推荐最新的商品，回答“是”；" +
                        "如果问题属于通用知识、历史、理论、主观分析，回答“否”。问题：%s",
                currentTime, query
        );

        try {
            log.info("调用 qwen2.5:3b 判断是否需要联网搜索，当前时间：{}，输入：{}", currentTime, query);
            String res = chatService.chat("qwen2.5:3b", prompt).trim();
            log.info("判断结果：{}", res);
            return res != null && res.contains("是");
        } catch (Exception e) {
            log.error("联网判断失败", e);
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