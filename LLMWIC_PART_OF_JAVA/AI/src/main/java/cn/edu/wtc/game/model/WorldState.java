package cn.edu.wtc.game.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import javax.xml.stream.Location;
import java.util.*;

@JsonIgnoreProperties(ignoreUnknown = true)
public class WorldState {
    private String name;
    private String currentLocation;
    private Map<String, Object> globalFlags = new HashMap<>();
    private Map<String, Location> locations = new HashMap<>();
    private String setting;      // 世界观设定
    private String worldBackground;  // 世界背景描述
    private String storyHook;    // 故事引子/剧情开端
    private String worldName;    // 世界名称（冗余字段）

    // ... clone 方法需要复制新增字段
    public WorldState clone() {
        WorldState cloned = new WorldState();
        cloned.name = this.name;
        cloned.setting = this.setting;
        cloned.currentLocation = this.currentLocation;
        cloned.globalFlags = new HashMap<>(this.globalFlags);
        cloned.locations = new HashMap<>(this.locations);
        cloned.worldBackground = this.worldBackground;
        cloned.storyHook = this.storyHook;
        cloned.worldName = this.worldName;
        return cloned;
    }

    // Getters and Setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSetting() { return setting; }
    public void setSetting(String setting) { this.setting = setting; }

    public String getCurrentLocation() { return currentLocation; }
    public void setCurrentLocation(String currentLocation) { this.currentLocation = currentLocation; }

    public Map<String, Object> getGlobalFlags() { return globalFlags; }
    public void setGlobalFlags(Map<String, Object> globalFlags) { this.globalFlags = globalFlags; }

    public Map<String, Location> getLocations() { return locations; }
    public void setLocations(Map<String, Location> locations) { this.locations = locations; }

    public String getWorldBackground() { return worldBackground; }
    public void setWorldBackground(String worldBackground) { this.worldBackground = worldBackground; }

    public String getStoryHook() { return storyHook; }
    public void setStoryHook(String storyHook) { this.storyHook = storyHook; }

    public String getWorldName() { return worldName; }
    public void setWorldName(String worldName) { this.worldName = worldName; }
}