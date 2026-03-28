package cn.edu.wtc.game.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "game_world_profile")
public class GameWorldProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, unique = true, length = 255)
    private String sessionId;

    @Column(name = "world_name", nullable = false, length = 100)
    private String worldName;

    @Column(name = "world_background", columnDefinition = "TEXT")
    private String worldBackground;

    @Column(name = "story_hook", columnDefinition = "TEXT")
    private String storyHook;

    @Column(name = "player_name", length = 100)
    private String playerName;

    @Column(name = "player_description", columnDefinition = "TEXT")
    private String playerDescription;

    @Column(name = "player_traits", columnDefinition = "JSON")
    private String playerTraits; // JSON 数组字符串

    @Column(name = "npcs", nullable = false, columnDefinition = "JSON")
    private String npcs; // JSON 数组字符串

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getWorldName() { return worldName; }
    public void setWorldName(String worldName) { this.worldName = worldName; }

    public String getWorldBackground() { return worldBackground; }
    public void setWorldBackground(String worldBackground) { this.worldBackground = worldBackground; }

    public String getStoryHook() { return storyHook; }
    public void setStoryHook(String storyHook) { this.storyHook = storyHook; }

    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }

    public String getPlayerDescription() { return playerDescription; }
    public void setPlayerDescription(String playerDescription) { this.playerDescription = playerDescription; }

    public String getPlayerTraits() { return playerTraits; }
    public void setPlayerTraits(String playerTraits) { this.playerTraits = playerTraits; }

    public String getNpcs() { return npcs; }
    public void setNpcs(String npcs) { this.npcs = npcs; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}