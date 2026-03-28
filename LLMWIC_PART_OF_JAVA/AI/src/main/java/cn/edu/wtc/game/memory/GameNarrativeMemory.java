package cn.edu.wtc.game.memory;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 小说叙事记忆 - 独立于 RAG 记忆系统
 */
@Entity
@Table(name = "game_narrative_memory")
public class GameNarrativeMemory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, length = 255)
    private String sessionId;

    @Column(name = "turn_number", nullable = false)
    private Integer turnNumber;

    @Column(name = "memory_type", nullable = false, length = 20)
    private String memoryType; // PLOT, CHOICE, CHARACTER, LOCATION, FORESHADOWING

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary; // 摘要，用于快速检索

    // 修复：使用 columnDefinition 明确指定 TINYINT
    @Column(name = "importance", nullable = false, columnDefinition = "TINYINT")
    private Integer importance = 1; // 1-5，重要程度

    @Column(name = "user_input", columnDefinition = "TEXT")
    private String userInput;

    @Column(name = "ai_response", columnDefinition = "TEXT")
    private String aiResponse;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public Integer getTurnNumber() { return turnNumber; }
    public void setTurnNumber(Integer turnNumber) { this.turnNumber = turnNumber; }

    public String getMemoryType() { return memoryType; }
    public void setMemoryType(String memoryType) { this.memoryType = memoryType; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public Integer getImportance() { return importance; }
    public void setImportance(Integer importance) { this.importance = importance; }

    public String getUserInput() { return userInput; }
    public void setUserInput(String userInput) { this.userInput = userInput; }

    public String getAiResponse() { return aiResponse; }
    public void setAiResponse(String aiResponse) { this.aiResponse = aiResponse; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}