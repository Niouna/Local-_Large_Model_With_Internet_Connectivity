package cn.edu.wtc.relay.orchestrator;

import cn.edu.wtc.memory.service.MemoryService;
import cn.edu.wtc.ollama.service.ChatService;
import cn.edu.wtc.ollama.service.SessionManager;
import cn.edu.wtc.relay.detector.IntentDetector;
import cn.edu.wtc.relay.search.SearchService;
import cn.edu.wtc.memory.entity.RagRequestLog;
import cn.edu.wtc.memory.repository.RagRequestLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

    @Autowired
    private RagRequestLogRepository ragLogRepository;

    // 新增：注入RAG模块是否启用的配置
    @Value("${rag.enabled:true}")
    private boolean isRagEnabled;

    /**
     * 带 Session 的对话入口 (支持记忆和联网)，现在接收前端的isWebSearchEnabled开关
     */
    /**
     * 带 Session 的对话入口 (支持记忆和联网)，现在接收前端的isWebSearchEnabled开关
     */
    public String chatWithRag(String sessionId, String model, String message, boolean isWebSearchEnabledFromFrontend) throws IOException {
        long startTime = System.currentTimeMillis();
        log.info("收到原始请求 -> SessionId: [{}], Model: [{}], Message: {}, WebSearchEnabledByUser: {}",
                sessionId, model, message, isWebSearchEnabledFromFrontend);

        if (model == null || model.trim().isEmpty()) {
            log.warn("⚠️ 检测到模型名称为空，强制使用默认模型 deepseek-r1:7b");
            model = "deepseek-r1:7b";
        }
        String actualModel = model.trim();
        Conversation conversation = sessionManager.getOrCreateSession(sessionId, actualModel);
        String effectiveSessionId = conversation.getSessionId();
        log.info("✅ 会话已就绪 -> 有效 SessionId: [{}], 模型：{}", effectiveSessionId, actualModel);

        String memoryContext = memoryService.assembleMemoryContext(effectiveSessionId);

        String finalPromptText;
        // 修改逻辑：根据用户设置的开关来决定流程
        if (isRagEnabled && isWebSearchEnabledFromFrontend) {
            // 用户开启联网开关，进行完整的RAG流程
            log.info("RAG已启用，用户开启联网开关，开始智能判断...");
            boolean needWebSearch = needsWebSearch(message);
            if (needWebSearch) {
                String realQuery = intentDetector.extractQuery(message);
                log.info("提取的查询词: {}", realQuery);
                String searchResult = performWebSearch(realQuery);
                String searchPart = String.format("基于以下搜索结果回答问题。\n搜索结果：%s\n\n用户问题：%s", searchResult, realQuery);
                finalPromptText = !memoryContext.isEmpty() ? "以下是历史信息（请参考这些信息回答用户的问题）：\n" + memoryContext + "\n\n" + searchPart : searchPart;
            } else {
                // 不需要联网搜索，但可以尝试本地检索（如果未来有实现的话）
                // for now, 直接使用历史信息和原始问题
                finalPromptText = !memoryContext.isEmpty() ? "以下是历史信息（请参考这些信息回答用户的问题）：\n" + memoryContext + "\n用户问题：" + message : message;
            }
        } else {
            // 用户未开启联网开关，或RAG模块被禁用
            if(isRagEnabled) {
                log.info("RAG已启用，但用户未开启联网开关，将基于历史信息和模型旧知识回答。");
                // 基于历史信息和模型自身旧知识回答，并告知时效性
                String contextPart = !memoryContext.isEmpty() ? "以下是历史信息（请参考这些信息回答用户的问题）：\n" + memoryContext + "\n\n" : "";
                finalPromptText = contextPart +
                        "注意：当前未启用实时联网搜索功能。您即将获得的回答是基于模型训练时的旧有知识，其信息时效性可能已过期。请谨慎参考。\n\n" +
                        "用户问题：" + message;
            } else {
                log.info("⚠️ RAG模块已被禁用 (rag.enabled=false)，将基于模型旧知识回答。");
                // RAG被禁用，直接构造prompt，并告知时效性
                String contextPart = !memoryContext.isEmpty() ? "以下是历史信息（请参考这些信息回答用户的问题）：\n" + memoryContext + "\n\n" : "";
                finalPromptText = contextPart +
                        "注意：系统RAG增强功能已被管理员禁用。您即将获得的回答是基于模型训练时的旧有知识，其信息时效性可能已过期。请谨慎参考。\n\n" +
                        "用户问题：" + message;
            }
        }

        log.info("最终 Prompt（前500字符）:\n{}", finalPromptText.length() > 500 ? finalPromptText.substring(0, 500) + "..." : finalPromptText);

        // 统一在这里调用模型，无论是否使用了RAG
        String answer = chatService.chatWithSession(effectiveSessionId, actualModel, finalPromptText);

        long endTime = System.currentTimeMillis();

        // --- 原有异步记忆逻辑 ---
        final String finalSessionId = effectiveSessionId;
        final String finalAnswer = answer;
        final String finalUserMsg = message; // 记录原始消息
        CompletableFuture.runAsync(() -> {
            try {
                memoryService.addL1Memory(finalSessionId, finalUserMsg, finalAnswer);
                log.debug("记忆已异步写入会话：{}", finalSessionId);
            } catch (Exception e) {
                log.error("异步记忆写入失败", e);
            }
        });

        // --- 新增：异步记录 RAG 请求日志 ---
        final String logSessionId = effectiveSessionId;
        final String logUserMsg = message;
        final boolean logNeedWeb = isRagEnabled && isWebSearchEnabledFromFrontend && needsWebSearch(message);
        final String logRealQuery = logNeedWeb ? intentDetector.extractQuery(message) : "";
        final String logSearchResult = logNeedWeb ? performWebSearch(logRealQuery) : "";
        final String logMemoryContext = memoryContext != null ? memoryContext : "";
        final String logFinalPrompt = finalPromptText;
        final String logAiResponse = answer;
        final int logProcessTime = (int) (endTime - startTime);
        CompletableFuture.runAsync(() -> {
            try {
                RagRequestLog logEntry = new RagRequestLog();
                logEntry.setSessionId(logSessionId);
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

    // 保留原有签名的重载方法，方便其他地方调用，但默认不开启web search
    public String chatWithRag(String model, String message) throws IOException {
        return chatWithRag(null, model, message, false);
    }

    // 新增：带sessionId但不指定web search状态的重载方法
    public String chatWithRag(String sessionId, String model, String message) throws IOException {
        return chatWithRag(sessionId, model, message, false);
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