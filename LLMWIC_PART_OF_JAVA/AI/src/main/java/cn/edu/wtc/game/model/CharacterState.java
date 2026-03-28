package cn.edu.wtc.game.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.*;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CharacterState {
    private PlayerCharacter player = new PlayerCharacter();
    private Map<String, NPC> npcs = new HashMap<>();
    private List<String> recentNPCs = new ArrayList<>();

    public CharacterState clone() {
        CharacterState cloned = new CharacterState();
        cloned.player = this.player.clone();
        cloned.npcs = new HashMap<>(this.npcs);
        cloned.recentNPCs = new ArrayList<>(this.recentNPCs);
        return cloned;
    }

    /**
     * 玩家角色类 - 使用标签化系统，无数值属性
     */
    public static class PlayerCharacter {
        private String name = "冒险者";
        private String description = "";           // 角色描述/背景故事
        private List<String> traits = new ArrayList<>();  // 性格标签（如：勇敢、睿智）
        private List<String> inventory = new ArrayList<>();  // 背包物品
        private List<String> skills = new ArrayList<>();     // 技能列表
        private Map<String, Integer> relationships = new HashMap<>();  // 与NPC的关系值

        public PlayerCharacter clone() {
            PlayerCharacter cloned = new PlayerCharacter();
            cloned.name = this.name;
            cloned.description = this.description;
            cloned.traits = new ArrayList<>(this.traits);
            cloned.inventory = new ArrayList<>(this.inventory);
            cloned.skills = new ArrayList<>(this.skills);
            cloned.relationships = new HashMap<>(this.relationships);
            return cloned;
        }

        // Getters and Setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public List<String> getTraits() { return traits; }
        public void setTraits(List<String> traits) { this.traits = traits; }

        public List<String> getInventory() { return inventory; }
        public void setInventory(List<String> inventory) { this.inventory = inventory; }

        public List<String> getSkills() { return skills; }
        public void setSkills(List<String> skills) { this.skills = skills; }

        public Map<String, Integer> getRelationships() { return relationships; }
        public void setRelationships(Map<String, Integer> relationships) { this.relationships = relationships; }
    }

    /**
     * NPC类 - 使用标签化系统
     */
    public static class NPC {
        private String name;
        private String role;              // 身份/角色（如：天穹城卫队长）
        private String description;       // 外貌描述、背景故事
        private String personality;       // 性格特点详细描述
        private String motivation;        // 核心动机/目标
        private List<String> traits = new ArrayList<>();  // 性格标签（如：腹黑、忠诚）
        private Integer relationship = 0;  // 与玩家的关系值
        private Map<String, Object> flags = new HashMap<>();

        // Getters and Setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getPersonality() { return personality; }
        public void setPersonality(String personality) { this.personality = personality; }

        public String getMotivation() { return motivation; }
        public void setMotivation(String motivation) { this.motivation = motivation; }

        public List<String> getTraits() { return traits; }
        public void setTraits(List<String> traits) { this.traits = traits; }

        public Integer getRelationship() { return relationship; }
        public void setRelationship(Integer relationship) { this.relationship = relationship; }

        public Map<String, Object> getFlags() { return flags; }
        public void setFlags(Map<String, Object> flags) { this.flags = flags; }
    }

    // Getters and Setters for CharacterState
    public PlayerCharacter getPlayer() { return player; }
    public void setPlayer(PlayerCharacter player) { this.player = player; }

    public Map<String, NPC> getNpcs() { return npcs; }
    public void setNpcs(Map<String, NPC> npcs) { this.npcs = npcs; }

    public List<String> getRecentNPCs() { return recentNPCs; }
    public void setRecentNPCs(List<String> recentNPCs) { this.recentNPCs = recentNPCs; }
}