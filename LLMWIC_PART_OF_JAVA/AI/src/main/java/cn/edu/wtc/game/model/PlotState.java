package cn.edu.wtc.game.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.*;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PlotState {
    private String currentPlotId = "main";
    private String currentNodeId = "start";
    private List<String> visitedNodes = new ArrayList<>();
    private Map<String, Boolean> flags = new HashMap<>();
    private List<String> activeForeshadowing = new ArrayList<>();
    private List<String> resolvedForeshadowing = new ArrayList<>();

    public PlotState clone() {
        PlotState cloned = new PlotState();
        cloned.currentPlotId = this.currentPlotId;
        cloned.currentNodeId = this.currentNodeId;
        cloned.visitedNodes = new ArrayList<>(this.visitedNodes);
        cloned.flags = new HashMap<>(this.flags);
        cloned.activeForeshadowing = new ArrayList<>(this.activeForeshadowing);
        cloned.resolvedForeshadowing = new ArrayList<>(this.resolvedForeshadowing);
        return cloned;
    }

    // 添加 @JsonIgnore 注解，避免被序列化
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isKeyPoint() {
        return currentNodeId.contains("key") ||
                currentNodeId.contains("climax") ||
                flags.getOrDefault("is_key_point", false);
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean hasMajorChange() {
        long trueFlags = flags.values().stream().filter(v -> v).count();
        return trueFlags > 5 || activeForeshadowing.size() > 3;
    }

    // Getters and Setters
    public String getCurrentPlotId() { return currentPlotId; }
    public void setCurrentPlotId(String currentPlotId) { this.currentPlotId = currentPlotId; }

    public String getCurrentNodeId() { return currentNodeId; }
    public void setCurrentNodeId(String currentNodeId) { this.currentNodeId = currentNodeId; }

    public List<String> getVisitedNodes() { return visitedNodes; }
    public void setVisitedNodes(List<String> visitedNodes) { this.visitedNodes = visitedNodes; }

    public Map<String, Boolean> getFlags() { return flags; }
    public void setFlags(Map<String, Boolean> flags) { this.flags = flags; }

    public List<String> getActiveForeshadowing() { return activeForeshadowing; }
    public void setActiveForeshadowing(List<String> activeForeshadowing) { this.activeForeshadowing = activeForeshadowing; }

    public List<String> getResolvedForeshadowing() { return resolvedForeshadowing; }
    public void setResolvedForeshadowing(List<String> resolvedForeshadowing) { this.resolvedForeshadowing = resolvedForeshadowing; }
}