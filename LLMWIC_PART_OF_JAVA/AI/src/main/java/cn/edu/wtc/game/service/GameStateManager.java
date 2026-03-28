package cn.edu.wtc.game.service;

import cn.edu.wtc.game.entity.GameStateSnapshot;
import cn.edu.wtc.game.entity.GameEventLog;
import cn.edu.wtc.game.model.CharacterState;
import cn.edu.wtc.game.model.GameState;
import cn.edu.wtc.game.model.GameStateDelta;
import cn.edu.wtc.game.repository.GameStateSnapshotRepository;
import cn.edu.wtc.game.repository.GameEventLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GameStateManager {

    private static final Logger log = LoggerFactory.getLogger(GameStateManager.class);

    private final ConcurrentHashMap<String, GameState> hotCache = new ConcurrentHashMap<>();

    @Autowired
    private GameStateSnapshotRepository snapshotRepo;

    @Autowired
    private GameEventLogRepository eventRepo;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public GameState getState(String sessionId) {
        GameState cached = hotCache.get(sessionId);
        if (cached != null && !isStale(cached)) {
            cached.updateLastAccessTime();
            return cached;
        }

        // 使用新的 findAllBySessionIdAndIsCurrentTrue 方法
        List<GameStateSnapshot> currentSnapshots = snapshotRepo.findAllBySessionIdAndIsCurrentTrue(sessionId);

        GameStateSnapshot latestSnapshot = null;

        if (!currentSnapshots.isEmpty()) {
            // 选择最新的快照
            latestSnapshot = currentSnapshots.stream()
                    .max((a, b) -> a.getSnapshotTime().compareTo(b.getSnapshotTime()))
                    .orElse(null);

            // 如果有多个当前快照，修复其他快照
            if (currentSnapshots.size() > 1) {
                log.warn("发现多个当前快照 for session {}, 共 {} 个，将修复并保留最新的", sessionId, currentSnapshots.size());
                for (GameStateSnapshot snap : currentSnapshots) {
                    if (snap != latestSnapshot) {
                        snap.setIsCurrent(false);
                        snapshotRepo.save(snap);
                        log.info("已将快照 {} 的 is_current 设为 false", snap.getId());
                    }
                }
            }
        }

        if (latestSnapshot != null) {
            GameState state = deserializeState(latestSnapshot);
            hotCache.put(sessionId, state);
            return state;
        }

        GameState newState = createNewState(sessionId);
        hotCache.put(sessionId, newState);
        return newState;
    }

    /**
     * 更新状态（带事件记录）
     */
    @Transactional
    public void updateState(String sessionId, GameStateDelta delta, GameEventLog event) {
        GameState current = getState(sessionId);
        GameState newState = current.clone();
        applyDelta(newState, delta);
        newState.incrementVersion();
        newState.setTurnNumber(current.getTurnNumber() + 1);
        newState.updateLastAccessTime();

        hotCache.put(sessionId, newState);

        // 记录事件
        if (event != null) {
            try {
                event.setSessionId(sessionId);
                event.setTurnNumber(current.getTurnNumber() + 1);
                event.setStateDelta(objectMapper.writeValueAsString(delta));
                eventRepo.save(event);
            } catch (Exception e) {
                log.error("保存事件失败", e);
            }
        }

        if (shouldCreateSnapshot(newState)) {
            createSnapshot(sessionId, newState);
        }
    }

    /**
     * 更新状态（无事件记录，简化版）
     */
    @Transactional
    public void updateState(String sessionId, GameStateDelta delta) {
        updateState(sessionId, delta, null);
    }

    private void createSnapshot(String sessionId, GameState state) {
        try {
            snapshotRepo.updateCurrentFlag(sessionId);

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

            snapshotRepo.save(snapshot);
            log.info("快照已创建: {}, 轮数: {}", sessionId, state.getTurnNumber());
        } catch (Exception e) {
            log.error("创建快照失败", e);
        }
    }

    private boolean shouldCreateSnapshot(GameState state) {
        if (state.getTurnNumber() % 5 == 0 && state.getTurnNumber() > 0) {
            return true;
        }
        if (state.getPlot().isKeyPoint()) {
            return true;
        }
        if (state.getPlot().hasMajorChange()) {
            return true;
        }
        return false;
    }

    private boolean isStale(GameState state) {
        return Duration.between(state.getLastAccessTime(), LocalDateTime.now())
                .toMinutes() > 30;
    }

    private GameState deserializeState(GameStateSnapshot snapshot) {
        try {
            GameState state = new GameState();
            state.setSessionId(snapshot.getSessionId());
            state.setVersion(snapshot.getVersion());
            state.setTurnNumber(snapshot.getTurnNumber());
            state.setNarrativeSummary(snapshot.getNarrativeMemory());

            if (snapshot.getWorldState() != null && !snapshot.getWorldState().isEmpty()) {
                state.setWorld(objectMapper.readValue(snapshot.getWorldState(),
                        cn.edu.wtc.game.model.WorldState.class));
            }
            if (snapshot.getCharacterState() != null && !snapshot.getCharacterState().isEmpty()) {
                state.setCharacters(objectMapper.readValue(snapshot.getCharacterState(),
                        cn.edu.wtc.game.model.CharacterState.class));
            }
            if (snapshot.getPlotState() != null && !snapshot.getPlotState().isEmpty()) {
                state.setPlot(objectMapper.readValue(snapshot.getPlotState(),
                        cn.edu.wtc.game.model.PlotState.class));
            }

            return state;
        } catch (Exception e) {
            log.error("反序列化状态失败", e);
            return createNewState(snapshot.getSessionId());
        }
    }

    private GameState createNewState(String sessionId) {
        GameState state = new GameState();
        state.setSessionId(sessionId);
        state.setTurnNumber(0);
        state.setVersion(1);

        // 确保所有子对象已初始化（但不预设内容）
        // WorldState 会在 getWorld() 中自动初始化
        // CharacterState 会在 getCharacters() 中自动初始化
        // PlotState 会在 getPlot() 中自动初始化

        // 不设置任何预设的世界背景或角色属性
        // 让用户通过世界管理接口来创建内容

        return state;
    }


    /**
     * 应用增量更新
     */
    private void applyDelta(GameState state, GameStateDelta delta) {
        if (delta == null) {
            return;
        }

        // 叙事摘要
        if (delta.getNarrativeDelta() != null) {
            state.setNarrativeSummary(delta.getNarrativeDelta());
        }

        // 世界观变化
        if (delta.getWorldDelta() != null) {
            if (delta.getWorldDelta().getCurrentLocation() != null) {
                state.getWorld().setCurrentLocation(delta.getWorldDelta().getCurrentLocation());
            }
            if (delta.getWorldDelta().getFlagsToSet() != null) {
                state.getWorld().getGlobalFlags().putAll(delta.getWorldDelta().getFlagsToSet());
            }
            if (delta.getWorldDelta().getFlagsToRemove() != null) {
                for (String flag : delta.getWorldDelta().getFlagsToRemove()) {
                    state.getWorld().getGlobalFlags().remove(flag);
                }
            }
            // 新增：世界背景更新
            if (delta.getWorldDelta().getWorldBackground() != null) {
                state.getWorld().setWorldBackground(delta.getWorldDelta().getWorldBackground());
            }
        }

        // 角色变化
        if (delta.getCharacterDelta() != null) {
            // 物品添加
            if (delta.getCharacterDelta().getItemsAdded() != null) {
                state.getCharacters().getPlayer().getInventory().addAll(delta.getCharacterDelta().getItemsAdded());
            }
            // 物品移除
            if (delta.getCharacterDelta().getItemsRemoved() != null) {
                state.getCharacters().getPlayer().getInventory().removeAll(delta.getCharacterDelta().getItemsRemoved());
            }
            // 关系变化
            if (delta.getCharacterDelta().getRelationshipChanges() != null) {
                state.getCharacters().getPlayer().getRelationships().putAll(delta.getCharacterDelta().getRelationshipChanges());
            }
            // 遇到NPC
            if (delta.getCharacterDelta().getNpcMet() != null) {
                if (!state.getCharacters().getRecentNPCs().contains(delta.getCharacterDelta().getNpcMet())) {
                    state.getCharacters().getRecentNPCs().add(delta.getCharacterDelta().getNpcMet());
                    // 保持最多5个
                    while (state.getCharacters().getRecentNPCs().size() > 5) {
                        state.getCharacters().getRecentNPCs().remove(0);
                    }
                }
            }
            // 新增：玩家特质变化
            if (delta.getCharacterDelta().getPlayerTraitsToAdd() != null && !delta.getCharacterDelta().getPlayerTraitsToAdd().isEmpty()) {
                state.getCharacters().getPlayer().getTraits().addAll(delta.getCharacterDelta().getPlayerTraitsToAdd());
            }
            if (delta.getCharacterDelta().getPlayerTraitsToRemove() != null && !delta.getCharacterDelta().getPlayerTraitsToRemove().isEmpty()) {
                state.getCharacters().getPlayer().getTraits().removeAll(delta.getCharacterDelta().getPlayerTraitsToRemove());
            }
            // 新增：NPC特质更新
            if (delta.getCharacterDelta().getNpcTraitsUpdates() != null && !delta.getCharacterDelta().getNpcTraitsUpdates().isEmpty()) {
                for (Map.Entry<String, List<String>> entry : delta.getCharacterDelta().getNpcTraitsUpdates().entrySet()) {
                    String npcId = entry.getKey();
                    List<String> traitsToAdd = entry.getValue();
                    CharacterState.NPC npc = state.getCharacters().getNpcs().get(npcId);
                    if (npc != null && traitsToAdd != null) {
                        npc.getTraits().addAll(traitsToAdd);
                    }
                }
            }
        }

        // 剧情变化
        if (delta.getPlotDelta() != null) {
            if (delta.getPlotDelta().getCurrentNodeId() != null) {
                state.getPlot().setCurrentNodeId(delta.getPlotDelta().getCurrentNodeId());
            }
            if (delta.getPlotDelta().getFlagsToSet() != null) {
                state.getPlot().getFlags().putAll(delta.getPlotDelta().getFlagsToSet());
            }
            if (delta.getPlotDelta().getForeshadowingAdded() != null) {
                state.getPlot().getActiveForeshadowing().addAll(delta.getPlotDelta().getForeshadowingAdded());
            }
            if (delta.getPlotDelta().getForeshadowingResolved() != null) {
                state.getPlot().getActiveForeshadowing().removeAll(delta.getPlotDelta().getForeshadowingResolved());
                state.getPlot().getResolvedForeshadowing().addAll(delta.getPlotDelta().getForeshadowingResolved());
            }
        }

        // 记录访问过的节点
        String currentNode = state.getPlot().getCurrentNodeId();
        if (currentNode != null && !state.getPlot().getVisitedNodes().contains(currentNode)) {
            state.getPlot().getVisitedNodes().add(currentNode);
        }
    }

    public void clearCache(String sessionId) {
        hotCache.remove(sessionId);
    }
}