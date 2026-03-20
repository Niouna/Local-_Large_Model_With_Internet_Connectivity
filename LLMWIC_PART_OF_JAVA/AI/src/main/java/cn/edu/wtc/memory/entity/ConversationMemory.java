package cn.edu.wtc.memory.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "conversation_memories", indexes = {
        @Index(name = "idx_session_level_active", columnList = "session_id, level, is_active"),
        @Index(name = "idx_created_at", columnList = "created_at")
})
public class ConversationMemory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, length = 255)
    private String sessionId;

    // 【已修复】显式指定数据库类型为 TINYINT
    @Column(name = "level", nullable = false, columnDefinition = "TINYINT")
    private Integer level;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "source_ids", length = 512)
    private String sourceIds;

    // Boolean 默认映射为 BIT 或 TINYINT(1)，通常不需要特殊定义，但如果报错也可以加上 columnDefinition = "TINYINT(1)"
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public ConversationMemory() {}

    public ConversationMemory(String sessionId, Integer level, String content, String sourceIds) {
        this.sessionId = sessionId;
        this.level = level;
        this.content = content;
        this.sourceIds = sourceIds;
    }

    // Getters and Setters (保持不变)
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public Integer getLevel() { return level; }
    public void setLevel(Integer level) { this.level = level; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getSourceIds() { return sourceIds; }
    public void setSourceIds(String sourceIds) { this.sourceIds = sourceIds; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}