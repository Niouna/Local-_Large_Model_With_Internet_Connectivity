package cn.edu.wtc.memory.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "rag_request_log")
public class RagRequestLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", length = 255)
    private String sessionId;

    @Column(name = "request_time", nullable = false)
    private LocalDateTime requestTime;

    @Column(name = "user_message", nullable = false, columnDefinition = "TEXT")
    private String userMessage;

    @Column(name = "need_web_search", nullable = false)
    private Boolean needWebSearch = false;

    @Column(name = "real_query", length = 500)
    private String realQuery;

    @Column(name = "search_result", columnDefinition = "MEDIUMTEXT")
    private String searchResult;

    @Column(name = "memory_context", columnDefinition = "MEDIUMTEXT")
    private String memoryContext;

    @Column(name = "final_prompt", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String finalPrompt;

    @Column(name = "ai_response", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String aiResponse;

    @Column(name = "process_time_ms")
    private Integer processTimeMs;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public LocalDateTime getRequestTime() { return requestTime; }
    public void setRequestTime(LocalDateTime requestTime) { this.requestTime = requestTime; }

    public String getUserMessage() { return userMessage; }
    public void setUserMessage(String userMessage) { this.userMessage = userMessage; }

    public Boolean getNeedWebSearch() { return needWebSearch; }
    public void setNeedWebSearch(Boolean needWebSearch) { this.needWebSearch = needWebSearch; }

    public String getRealQuery() { return realQuery; }
    public void setRealQuery(String realQuery) { this.realQuery = realQuery; }

    public String getSearchResult() { return searchResult; }
    public void setSearchResult(String searchResult) { this.searchResult = searchResult; }

    public String getMemoryContext() { return memoryContext; }
    public void setMemoryContext(String memoryContext) { this.memoryContext = memoryContext; }

    public String getFinalPrompt() { return finalPrompt; }
    public void setFinalPrompt(String finalPrompt) { this.finalPrompt = finalPrompt; }

    public String getAiResponse() { return aiResponse; }
    public void setAiResponse(String aiResponse) { this.aiResponse = aiResponse; }

    public Integer getProcessTimeMs() { return processTimeMs; }
    public void setProcessTimeMs(Integer processTimeMs) { this.processTimeMs = processTimeMs; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}