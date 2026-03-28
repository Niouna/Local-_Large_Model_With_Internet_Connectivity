package cn.edu.wtc.controller;

import cn.edu.wtc.game.entity.CharacterTraitDefinition;
import cn.edu.wtc.game.entity.GameStateSnapshot;
import cn.edu.wtc.game.entity.GameWorldProfile;
import cn.edu.wtc.game.model.*;
import cn.edu.wtc.game.repository.CharacterTraitDefinitionRepository;
import cn.edu.wtc.game.repository.GameStateSnapshotRepository;
import cn.edu.wtc.game.repository.GameWorldProfileRepository;
import cn.edu.wtc.game.service.GameStateManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 世界管理控制器
 * 提供世界创建、修改、查询等功能
 */
@RestController
@RequestMapping("/v1/game")
public class WorldManagerController {

    private static final Logger log = LoggerFactory.getLogger(WorldManagerController.class);

    @Autowired
    private GameStateManager gameStateManager;

    @Autowired
    private GameStateSnapshotRepository snapshotRepository;

    @Autowired
    private CharacterTraitDefinitionRepository traitRepository;

    @Autowired
    private GameWorldProfileRepository profileRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 获取所有可用特质列表
     * GET /v1/game/traits
     */
    @GetMapping("/traits")
    public List<Map<String, Object>> getAllTraits() {
        List<CharacterTraitDefinition> traits = traitRepository.findAll();
        return traits.stream().map(t -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", t.getId());
            map.put("traitName", t.getTraitName());
            map.put("description", t.getDescription());
            map.put("example", t.getExample());
            return map;
        }).collect(Collectors.toList());
    }

    /**
     * 获取所有世界列表
     * GET /v1/game/worlds
     * 返回所有有快照的会话，按最后活动时间排序
     */
    @GetMapping("/worlds")
    public List<Map<String, Object>> getAllWorlds() {
        // 获取所有会话的最新快照（每个会话取最新的一条）
        // 注意：这里需要查询所有会话，但JPA不支持直接GROUP BY，我们通过查询所有快照然后分组
        List<GameStateSnapshot> allSnapshots = snapshotRepository.findAll();

        // 按sessionId分组，取每个会话最新的一条
        Map<String, GameStateSnapshot> latestBySession = new HashMap<>();
        for (GameStateSnapshot snapshot : allSnapshots) {
            String sessionId = snapshot.getSessionId();
            GameStateSnapshot existing = latestBySession.get(sessionId);
            if (existing == null || snapshot.getSnapshotTime().isAfter(existing.getSnapshotTime())) {
                latestBySession.put(sessionId, snapshot);
            }
        }

        List<Map<String, Object>> worlds = new ArrayList<>();
        for (Map.Entry<String, GameStateSnapshot> entry : latestBySession.entrySet()) {
            String sessionId = entry.getKey();
            GameStateSnapshot snapshot = entry.getValue();

            try {
                // 解析世界名称
                String worldName = "未命名世界";
                if (snapshot.getWorldState() != null && !snapshot.getWorldState().isEmpty()) {
                    WorldState world = objectMapper.readValue(snapshot.getWorldState(), WorldState.class);
                    worldName = world.getName() != null ? world.getName() : "未命名世界";
                }

                Map<String, Object> worldInfo = new HashMap<>();
                worldInfo.put("sessionId", sessionId);
                worldInfo.put("worldName", worldName);
                worldInfo.put("turnNumber", snapshot.getTurnNumber());
                worldInfo.put("lastUpdate", snapshot.getSnapshotTime());
                worldInfo.put("snapshotId", snapshot.getId());
                worlds.add(worldInfo);
            } catch (Exception e) {
                log.error("解析世界状态失败: {}", sessionId, e);
            }
        }

        // 按最后更新时间倒序排序
        worlds.sort((a, b) -> {
            LocalDateTime timeA = (LocalDateTime) a.get("lastUpdate");
            LocalDateTime timeB = (LocalDateTime) b.get("lastUpdate");
            return timeB.compareTo(timeA);
        });

        return worlds;
    }

    /**
     * 获取指定世界的详细信息
     * GET /v1/game/world/{sessionId}
     */
    @GetMapping("/world/{sessionId}")
    public Map<String, Object> getWorldDetail(@PathVariable String sessionId) {
        Map<String, Object> result = new HashMap<>();

        try {
            GameState state = gameStateManager.getState(sessionId);

            result.put("sessionId", state.getSessionId());
            result.put("worldName", state.getWorld().getName());
            result.put("worldBackground", state.getWorld().getWorldBackground());
            result.put("playerName", state.getCharacters().getPlayer().getName());
            result.put("playerTraits", state.getCharacters().getPlayer().getTraits());

            // 转换NPC列表为前端格式
            List<Map<String, Object>> npcs = new ArrayList<>();
            for (Map.Entry<String, CharacterState.NPC> entry : state.getCharacters().getNpcs().entrySet()) {
                CharacterState.NPC npc = entry.getValue();
                Map<String, Object> npcInfo = new HashMap<>();
                npcInfo.put("id", entry.getKey());
                npcInfo.put("name", npc.getName());
                npcInfo.put("traits", npc.getTraits());
                npcInfo.put("description", npc.getDescription());
                npcInfo.put("relationship", npc.getRelationship());
                npcs.add(npcInfo);
            }
            result.put("npcs", npcs);
            result.put("turnNumber", state.getTurnNumber());
            result.put("currentLocation", state.getWorld().getCurrentLocation());

            result.put("status", "ok");
        } catch (Exception e) {
            log.error("获取世界详情失败: {}", sessionId, e);
            result.put("status", "error");
            result.put("message", e.getMessage());
        }

        return result;
    }

    /**
     * 创建新世界
     * POST /v1/game/world
     *
     * 请求体示例:
     * {
     *   "worldName": "奇幻大陆",
     *   "worldBackground": "这是一个充满魔法和剑的世界...",
     *   "playerName": "冒险者",
     *   "playerTraits": ["勇敢", "睿智"],
     *   "npcs": [
     *     {"name": "神秘老者", "traits": ["睿智", "腹黑"]},
     *     {"name": "精灵公主", "traits": ["傲娇"]}
     *   ]
     * }
     */
    @PostMapping("/world")
    public Map<String, Object> createWorld(@RequestBody Map<String, Object> request) {
        Map<String, Object> result = new HashMap<>();
        try {
            String sessionId = "world_" + UUID.randomUUID().toString().replace("-", "");

            // 1. 解析请求参数
            String worldName = (String) request.getOrDefault("worldName", "新世界");
            String worldBackground = (String) request.getOrDefault("worldBackground", "");
            String storyHook = (String) request.getOrDefault("storyHook", "");
            String playerName = (String) request.getOrDefault("playerName", "冒险者");
            String playerDescription = (String) request.getOrDefault("playerDescription", "");
            List<String> playerTraits = (List<String>) request.getOrDefault("playerTraits", new ArrayList<>());
            List<Map<String, Object>> npcs = (List<Map<String, Object>>) request.getOrDefault("npcs", new ArrayList<>());

            // 2. 保存世界档案表
            GameWorldProfile profile = new GameWorldProfile();
            profile.setSessionId(sessionId);
            profile.setWorldName(worldName);
            profile.setWorldBackground(worldBackground);
            profile.setStoryHook(storyHook);
            profile.setPlayerName(playerName);
            profile.setPlayerDescription(playerDescription);
            profile.setPlayerTraits(objectMapper.writeValueAsString(playerTraits));
            profile.setNpcs(objectMapper.writeValueAsString(npcs));
            profileRepository.save(profile);

            // 3. 创建初始游戏状态（快照）
            GameState state = new GameState();
            state.setSessionId(sessionId);
            state.setTurnNumber(0);
            state.setVersion(1);

            // 世界静态部分
            state.getWorld().setWorldName(worldName);
            state.getWorld().setWorldBackground(worldBackground);
            state.getWorld().setStoryHook(storyHook);
            state.getWorld().setCurrentLocation("起始地点");

            // 玩家静态部分
            state.getCharacters().getPlayer().setName(playerName);
            state.getCharacters().getPlayer().setDescription(playerDescription);
            state.getCharacters().getPlayer().setTraits(playerTraits);

            // NPC 静态部分（复制到快照）
            for (Map<String, Object> npcData : npcs) {
                String npcName = (String) npcData.get("name");
                if (npcName == null || npcName.trim().isEmpty()) continue;

                CharacterState.NPC npc = new CharacterState.NPC();
                npc.setName(npcName);
                npc.setRole((String) npcData.getOrDefault("role", ""));
                npc.setDescription((String) npcData.getOrDefault("description", ""));
                npc.setPersonality((String) npcData.getOrDefault("personality", ""));
                npc.setMotivation((String) npcData.getOrDefault("motivation", ""));
                List<String> traits = (List<String>) npcData.getOrDefault("traits", new ArrayList<>());
                npc.setTraits(traits);
                Object relObj = npcData.get("relationship");
                npc.setRelationship(relObj instanceof Number ? ((Number) relObj).intValue() : 0);
                String npcId = "npc_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
                state.getCharacters().getNpcs().put(npcId, npc);
            }

            // 创建快照
            GameStateSnapshot snapshot = new GameStateSnapshot();
            snapshot.setSessionId(sessionId);
            snapshot.setWorldState(objectMapper.writeValueAsString(state.getWorld()));
            snapshot.setCharacterState(objectMapper.writeValueAsString(state.getCharacters()));
            snapshot.setPlotState(objectMapper.writeValueAsString(state.getPlot()));
            snapshot.setNarrativeMemory(state.getNarrativeSummary());
            snapshot.setVersion(state.getVersion());
            snapshot.setTurnNumber(state.getTurnNumber());
            snapshot.setIsCurrent(true);
            snapshot.setSnapshotTime(LocalDateTime.now());
            snapshotRepository.save(snapshot);

            result.put("status", "ok");
            result.put("sessionId", sessionId);
            result.put("message", "世界创建成功！");

        } catch (Exception e) {
            log.error("创建世界失败", e);
            result.put("status", "error");
            result.put("message", "创建失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 更新现有世界
     * PUT /v1/game/world/{sessionId}
     *
     * 请求体示例:
     * {
     *   "worldBackground": "新的世界背景...",
     *   "playerName": "新名字",
     *   "playerTraits": ["勇敢", "睿智", "仁慈"],
     *   "npcs": [
     *     {"id": "npc_xxx", "name": "神秘老者", "traits": ["睿智"], "description": "..."},
     *     {"id": "npc_new", "name": "新NPC", "traits": ["腹黑"]}
     *   ]
     * }
     */
    @PutMapping("/world/{sessionId}")
    public Map<String, Object> updateWorld(
            @PathVariable String sessionId,
            @RequestBody Map<String, Object> request) {

        Map<String, Object> result = new HashMap<>();

        try {
            GameState state = gameStateManager.getState(sessionId);

            // 更新世界背景
            String newWorldBackground = (String) request.get("worldBackground");
            if (newWorldBackground != null) {
                state.getWorld().setWorldBackground(newWorldBackground);
            }

            // 更新玩家名称
            String newPlayerName = (String) request.get("playerName");
            if (newPlayerName != null && !newPlayerName.trim().isEmpty()) {
                state.getCharacters().getPlayer().setName(newPlayerName);
            }

            // 更新玩家特质
            @SuppressWarnings("unchecked")
            List<String> newPlayerTraits = (List<String>) request.get("playerTraits");
            if (newPlayerTraits != null) {
                state.getCharacters().getPlayer().setTraits(newPlayerTraits);
            }

            // 更新NPC
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> npcsData = (List<Map<String, Object>>) request.get("npcs");
            if (npcsData != null) {
                // 获取当前NPC列表
                Map<String, CharacterState.NPC> currentNpcs = state.getCharacters().getNpcs();

                // 用于记录前端传递的NPC ID
                Set<String> processedIds = new HashSet<>();

                for (Map<String, Object> npcData : npcsData) {
                    String npcId = (String) npcData.get("id");
                    String npcName = (String) npcData.get("name");

                    if (npcName == null || npcName.trim().isEmpty()) continue;

                    CharacterState.NPC npc;
                    if (npcId != null && currentNpcs.containsKey(npcId)) {
                        // 更新现有NPC
                        npc = currentNpcs.get(npcId);
                        processedIds.add(npcId);
                    } else {
                        // 创建新NPC
                        npc = new CharacterState.NPC();
                        npcId = "npc_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
                        processedIds.add(npcId);
                    }

                    npc.setName(npcName);

                    @SuppressWarnings("unchecked")
                    List<String> traits = (List<String>) npcData.getOrDefault("traits", new ArrayList<>());
                    npc.setTraits(traits);

                    String description = (String) npcData.get("description");
                    if (description != null) npc.setDescription(description);

                    Integer relationship = (Integer) npcData.get("relationship");
                    if (relationship != null) npc.setRelationship(relationship);

                    state.getCharacters().getNpcs().put(npcId, npc);
                }

                // 移除前端没有传递的NPC
                List<String> toRemove = new ArrayList<>();
                for (String id : currentNpcs.keySet()) {
                    if (!processedIds.contains(id)) {
                        toRemove.add(id);
                    }
                }
                for (String id : toRemove) {
                    currentNpcs.remove(id);
                }
            }

            // 增加版本号
            state.incrementVersion();
            state.updateLastAccessTime();

            // 创建新快照
            snapshotRepository.updateCurrentFlag(sessionId);

            GameStateSnapshot snapshot = new GameStateSnapshot();
            snapshot.setSessionId(sessionId);
            snapshot.setWorldState(objectMapper.writeValueAsString(state.getWorld()));
            snapshot.setCharacterState(objectMapper.writeValueAsString(state.getCharacters()));
            snapshot.setPlotState(objectMapper.writeValueAsString(state.getPlot()));
            snapshot.setNarrativeMemory(state.getNarrativeSummary());
            snapshot.setVersion(state.getVersion());
            snapshot.setTurnNumber(state.getTurnNumber());
            snapshot.setIsCurrent(true);
            snapshot.setSnapshotTime(LocalDateTime.now());

            snapshotRepository.save(snapshot);

            // 更新缓存
            gameStateManager.clearCache(sessionId);

            result.put("status", "ok");
            result.put("message", "世界更新成功！");

            log.info("世界更新成功: sessionId={}", sessionId);

        } catch (Exception e) {
            log.error("更新世界失败: {}", sessionId, e);
            result.put("status", "error");
            result.put("message", "更新失败: " + e.getMessage());
        }

        return result;
    }

    @GetMapping("/profile/{sessionId}")
    public Map<String, Object> getWorldProfile(@PathVariable String sessionId) {
        Map<String, Object> result = new HashMap<>();
        try {
            Optional<GameWorldProfile> opt = profileRepository.findBySessionId(sessionId);
            if (opt.isPresent()) {
                GameWorldProfile profile = opt.get();
                result.put("sessionId", profile.getSessionId());
                result.put("worldName", profile.getWorldName());
                result.put("worldBackground", profile.getWorldBackground());
                result.put("storyHook", profile.getStoryHook());
                result.put("playerName", profile.getPlayerName());
                result.put("playerDescription", profile.getPlayerDescription());
                result.put("playerTraits", objectMapper.readValue(profile.getPlayerTraits(), List.class));
                result.put("npcs", objectMapper.readValue(profile.getNpcs(), List.class));
                result.put("status", "ok");
            } else {
                result.put("status", "error");
                result.put("message", "世界档案不存在");
            }
        } catch (Exception e) {
            log.error("获取世界档案失败", e);
            result.put("status", "error");
            result.put("message", e.getMessage());
        }
        return result;
    }


    // ======================== 特质定义管理（供管理后台使用） ========================

    /**
     * 创建新特质
     */
    @PostMapping("/traits")
    public Map<String, Object> createTrait(@RequestBody Map<String, String> request) {
        Map<String, Object> result = new HashMap<>();
        try {
            String traitName = request.get("traitName");
            String description = request.get("description");
            String example = request.get("example");

            if (traitName == null || traitName.trim().isEmpty()) {
                result.put("status", "error");
                result.put("message", "特质名称不能为空");
                return result;
            }

            if (traitRepository.existsByTraitName(traitName)) {
                result.put("status", "error");
                result.put("message", "特质名称已存在");
                return result;
            }

            CharacterTraitDefinition trait = new CharacterTraitDefinition();
            trait.setTraitName(traitName.trim());
            trait.setDescription(description != null ? description.trim() : "");
            trait.setExample(example != null ? example.trim() : "");

            traitRepository.save(trait);

            result.put("status", "ok");
            result.put("message", "特质创建成功");
            result.put("id", trait.getId());
        } catch (Exception e) {
            log.error("创建特质失败", e);
            result.put("status", "error");
            result.put("message", e.getMessage());
        }
        return result;
    }

    /**
     * 更新特质
     */
    @PutMapping("/traits/{id}")
    public Map<String, Object> updateTrait(@PathVariable Long id, @RequestBody Map<String, String> request) {
        Map<String, Object> result = new HashMap<>();
        try {
            CharacterTraitDefinition trait = traitRepository.findById(id).orElse(null);
            if (trait == null) {
                result.put("status", "error");
                result.put("message", "特质不存在");
                return result;
            }

            String traitName = request.get("traitName");
            String description = request.get("description");
            String example = request.get("example");

            if (traitName != null && !traitName.trim().isEmpty()) {
                // 检查新名称是否与其他特质冲突
                if (!traitName.equals(trait.getTraitName()) && traitRepository.existsByTraitName(traitName)) {
                    result.put("status", "error");
                    result.put("message", "特质名称已存在");
                    return result;
                }
                trait.setTraitName(traitName.trim());
            }
            if (description != null) trait.setDescription(description.trim());
            if (example != null) trait.setExample(example.trim());

            traitRepository.save(trait);

            result.put("status", "ok");
            result.put("message", "特质更新成功");
        } catch (Exception e) {
            log.error("更新特质失败", e);
            result.put("status", "error");
            result.put("message", e.getMessage());
        }
        return result;
    }

    /**
     * 删除特质
     */
    @DeleteMapping("/traits/{id}")
    public Map<String, Object> deleteTrait(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        try {
            if (!traitRepository.existsById(id)) {
                result.put("status", "error");
                result.put("message", "特质不存在");
                return result;
            }
            traitRepository.deleteById(id);
            result.put("status", "ok");
            result.put("message", "特质删除成功");
        } catch (Exception e) {
            log.error("删除特质失败", e);
            result.put("status", "error");
            result.put("message", e.getMessage());
        }
        return result;
    }
}