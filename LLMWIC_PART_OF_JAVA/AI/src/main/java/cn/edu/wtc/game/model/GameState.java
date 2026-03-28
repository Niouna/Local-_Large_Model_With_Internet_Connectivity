package cn.edu.wtc.game.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;
import java.util.*;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GameState {
    private String sessionId;
    private Integer version = 1;
    private Integer turnNumber = 0;
    private LocalDateTime lastAccessTime = LocalDateTime.now();

    private WorldState world = new WorldState();
    private CharacterState characters = new CharacterState();
    private PlotState plot = new PlotState();
    private String narrativeSummary = "";

    private String model = "deepseek-r1:7b";
    private Map<String, Object> config = new HashMap<>();

    public void incrementVersion() {
        this.version++;
    }

    public void updateLastAccessTime() {
        this.lastAccessTime = LocalDateTime.now();
    }

    public GameState clone() {
        GameState cloned = new GameState();
        cloned.sessionId = this.sessionId;
        cloned.version = this.version;
        cloned.turnNumber = this.turnNumber;
        cloned.lastAccessTime = this.lastAccessTime;
        cloned.world = this.world != null ? this.world.clone() : new WorldState();
        cloned.characters = this.characters != null ? this.characters.clone() : new CharacterState();
        cloned.plot = this.plot != null ? this.plot.clone() : new PlotState();
        cloned.narrativeSummary = this.narrativeSummary;
        cloned.model = this.model;
        cloned.config = new HashMap<>(this.config);
        return cloned;
    }

    // Getters and Setters
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

    public Integer getTurnNumber() { return turnNumber; }
    public void setTurnNumber(Integer turnNumber) { this.turnNumber = turnNumber; }

    public LocalDateTime getLastAccessTime() { return lastAccessTime; }
    public void setLastAccessTime(LocalDateTime lastAccessTime) { this.lastAccessTime = lastAccessTime; }

    public WorldState getWorld() {
        if (world == null) {
            world = new WorldState();
        }
        return world;
    }
    public void setWorld(WorldState world) { this.world = world; }

    public CharacterState getCharacters() {
        if (characters == null) {
            characters = new CharacterState();
        }
        return characters;
    }
    public void setCharacters(CharacterState characters) { this.characters = characters; }

    public PlotState getPlot() {
        if (plot == null) {
            plot = new PlotState();
        }
        return plot;
    }
    public void setPlot(PlotState plot) { this.plot = plot; }

    public String getNarrativeSummary() { return narrativeSummary; }
    public void setNarrativeSummary(String narrativeSummary) { this.narrativeSummary = narrativeSummary; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public Map<String, Object> getConfig() { return config; }
    public void setConfig(Map<String, Object> config) { this.config = config; }
}