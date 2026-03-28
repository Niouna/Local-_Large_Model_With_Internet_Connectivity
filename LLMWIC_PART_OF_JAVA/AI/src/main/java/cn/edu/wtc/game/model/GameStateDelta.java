package cn.edu.wtc.game.model;

import java.util.*;

public class GameStateDelta {
    private WorldStateDelta worldDelta;
    private CharacterStateDelta characterDelta;
    private PlotStateDelta plotDelta;
    private String narrativeDelta;
//    private List<String> traitsToAdd;
//    private List<String> traitsToRemove;

    public static class WorldStateDelta {
        private String currentLocation;
        private Map<String, Object> flagsToSet;
        private List<String> flagsToRemove;
        private String worldBackground;  // 新增：世界背景更新

        // Getters and Setters
        public String getCurrentLocation() {
            return currentLocation;
        }

        public void setCurrentLocation(String currentLocation) {
            this.currentLocation = currentLocation;
        }

        public Map<String, Object> getFlagsToSet() {
            return flagsToSet;
        }

        public void setFlagsToSet(Map<String, Object> flagsToSet) {
            this.flagsToSet = flagsToSet;
        }

        public List<String> getFlagsToRemove() {
            return flagsToRemove;
        }

        public void setFlagsToRemove(List<String> flagsToRemove) {
            this.flagsToRemove = flagsToRemove;
        }

        public String getWorldBackground() {
            return worldBackground;
        }

        public void setWorldBackground(String worldBackground) {
            this.worldBackground = worldBackground;
        }
    }

    public static class CharacterStateDelta {
        private List<String> itemsAdded;
        private List<String> itemsRemoved;
        private Map<String, Integer> relationshipChanges;
        private String npcMet;

        // 新增：玩家特质变化
        private List<String> playerTraitsToAdd;
        private List<String> playerTraitsToRemove;

        // 新增：NPC特质变化（key为NPC ID，value为要添加的特质列表）
        private Map<String, List<String>> npcTraitsUpdates;

        // 移除：attributeChanges（不再使用数值属性）
        // private Map<String, Integer> attributeChanges;  // 删除这一行

        // Getters and Setters
        public List<String> getItemsAdded() { return itemsAdded; }
        public void setItemsAdded(List<String> itemsAdded) { this.itemsAdded = itemsAdded; }

        public List<String> getItemsRemoved() { return itemsRemoved; }
        public void setItemsRemoved(List<String> itemsRemoved) { this.itemsRemoved = itemsRemoved; }

        public Map<String, Integer> getRelationshipChanges() { return relationshipChanges; }
        public void setRelationshipChanges(Map<String, Integer> relationshipChanges) { this.relationshipChanges = relationshipChanges; }

        public String getNpcMet() { return npcMet; }
        public void setNpcMet(String npcMet) { this.npcMet = npcMet; }

        public List<String> getPlayerTraitsToAdd() { return playerTraitsToAdd; }
        public void setPlayerTraitsToAdd(List<String> playerTraitsToAdd) { this.playerTraitsToAdd = playerTraitsToAdd; }

        public List<String> getPlayerTraitsToRemove() { return playerTraitsToRemove; }
        public void setPlayerTraitsToRemove(List<String> playerTraitsToRemove) { this.playerTraitsToRemove = playerTraitsToRemove; }

        public Map<String, List<String>> getNpcTraitsUpdates() { return npcTraitsUpdates; }
        public void setNpcTraitsUpdates(Map<String, List<String>> npcTraitsUpdates) { this.npcTraitsUpdates = npcTraitsUpdates; }
    }

    public static class PlotStateDelta {
        private String currentNodeId;
        private Map<String, Boolean> flagsToSet;
        private List<String> foreshadowingAdded;
        private List<String> foreshadowingResolved;

        // Getters and Setters
        public String getCurrentNodeId() { return currentNodeId; }
        public void setCurrentNodeId(String currentNodeId) { this.currentNodeId = currentNodeId; }
        public Map<String, Boolean> getFlagsToSet() { return flagsToSet; }
        public void setFlagsToSet(Map<String, Boolean> flagsToSet) { this.flagsToSet = flagsToSet; }
        public List<String> getForeshadowingAdded() { return foreshadowingAdded; }
        public void setForeshadowingAdded(List<String> foreshadowingAdded) { this.foreshadowingAdded = foreshadowingAdded; }
        public List<String> getForeshadowingResolved() { return foreshadowingResolved; }
        public void setForeshadowingResolved(List<String> foreshadowingResolved) { this.foreshadowingResolved = foreshadowingResolved; }
    }

    // Getters and Setters
    public WorldStateDelta getWorldDelta() { return worldDelta; }
    public void setWorldDelta(WorldStateDelta worldDelta) { this.worldDelta = worldDelta; }
    public CharacterStateDelta getCharacterDelta() { return characterDelta; }
    public void setCharacterDelta(CharacterStateDelta characterDelta) { this.characterDelta = characterDelta; }
    public PlotStateDelta getPlotDelta() { return plotDelta; }
    public void setPlotDelta(PlotStateDelta plotDelta) { this.plotDelta = plotDelta; }
    public String getNarrativeDelta() { return narrativeDelta; }
    public void setNarrativeDelta(String narrativeDelta) { this.narrativeDelta = narrativeDelta; }

//    public List<String> getTraitsToAdd() {
//        return traitsToAdd;
//    }
//    public void setTraitsToAdd(List<String> traitsToAdd) {
//        this.traitsToAdd = traitsToAdd;
//    }
//    public List<String> getTraitsToRemove() {
//        return traitsToRemove;
//    }
//    public void setTraitsToRemove(List<String> traitsToRemove) {
//        this.traitsToRemove = traitsToRemove;
//    }
}