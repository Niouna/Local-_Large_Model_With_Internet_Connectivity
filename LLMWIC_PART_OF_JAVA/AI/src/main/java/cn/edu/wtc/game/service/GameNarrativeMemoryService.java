package cn.edu.wtc.game.service;

import cn.edu.wtc.game.memory.GameNarrativeMemory;
import cn.edu.wtc.game.repository.GameNarrativeMemoryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class GameNarrativeMemoryService {

    private static final Logger log = LoggerFactory.getLogger(GameNarrativeMemoryService.class);

    @Autowired
    private GameNarrativeMemoryRepository memoryRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 记录剧情记忆
     */
    @Transactional
    public void recordPlot(String sessionId, int turnNumber, String userInput, String aiResponse,
                           String location, Map<String, Object> changes) {
        GameNarrativeMemory memory = new GameNarrativeMemory();
        memory.setSessionId(sessionId);
        memory.setTurnNumber(turnNumber);
        memory.setMemoryType("PLOT");

        // 构建内容
        Map<String, Object> content = new HashMap<>();
        content.put("location", location);
        content.put("userInput", userInput);
        content.put("aiResponse", aiResponse.length() > 200 ? aiResponse.substring(0, 200) : aiResponse);
        content.put("changes", changes);

        try {
            memory.setContent(objectMapper.writeValueAsString(content));
            memory.setSummary(extractSummary(userInput, aiResponse));
            memory.setImportance(calculateImportance(changes));
            memory.setUserInput(userInput);
            memory.setAiResponse(aiResponse);
            memoryRepository.save(memory);
        } catch (Exception e) {
            log.error("记录剧情记忆失败", e);
        }
    }

    /**
     * 记录玩家选择
     */
    @Transactional
    public void recordChoice(String sessionId, int turnNumber, String choice, String action) {
        GameNarrativeMemory memory = new GameNarrativeMemory();
        memory.setSessionId(sessionId);
        memory.setTurnNumber(turnNumber);
        memory.setMemoryType("CHOICE");
        memory.setContent(String.format("{\"choice\":\"%s\",\"action\":\"%s\"}", choice, action));
        memory.setSummary("玩家选择: " + choice);
        memory.setImportance(3);
        memory.setUserInput(choice);
        memoryRepository.save(memory);
    }

    /**
     * 记录伏笔
     */
    @Transactional
    public void recordForeshadowing(String sessionId, int turnNumber, String foreshadowing, boolean isNew) {
        GameNarrativeMemory memory = new GameNarrativeMemory();
        memory.setSessionId(sessionId);
        memory.setTurnNumber(turnNumber);
        memory.setMemoryType("FORESHADOWING");
        memory.setContent(foreshadowing);
        memory.setSummary(isNew ? "新伏笔: " + foreshadowing : "伏笔回收: " + foreshadowing);
        memory.setImportance(4);
        memoryRepository.save(memory);
    }

    /**
     * 获取最近 N 条记忆（用于构建 Prompt）
     */
    @Transactional(readOnly = true)
    public String getRecentMemoriesForPrompt(String sessionId, int limit) {
        List<GameNarrativeMemory> memories = memoryRepository.findRecentMemories(sessionId, limit);
        if (memories.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("【近期剧情回顾】\n");
        for (GameNarrativeMemory memory : memories) {
            sb.append("- 第").append(memory.getTurnNumber()).append("轮: ");
            sb.append(memory.getSummary()).append("\n");
        }
        return sb.toString();
    }

    /**
     * 获取重要记忆（重要性 >= 3）
     */
    @Transactional(readOnly = true)
    public String getImportantMemoriesForPrompt(String sessionId) {
        List<GameNarrativeMemory> memories = memoryRepository.findImportantMemories(sessionId, 3);
        if (memories.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("【重要剧情节点】\n");
        for (GameNarrativeMemory memory : memories) {
            sb.append("- ").append(memory.getSummary()).append("\n");
        }
        return sb.toString();
    }

    /**
     * 获取活跃伏笔
     */
    @Transactional(readOnly = true)
    public List<String> getActiveForeshadowing(String sessionId) {
        List<GameNarrativeMemory> memories = memoryRepository.findBySessionIdAndMemoryTypeOrderByTurnNumberDesc(
                sessionId, "FORESHADOWING");

        return memories.stream()
                .limit(5)
                .map(GameNarrativeMemory::getSummary)
                .collect(Collectors.toList());
    }

    /**
     * 清空会话记忆
     */
    @Transactional
    public void clearSession(String sessionId) {
        memoryRepository.deleteBySessionId(sessionId);
        log.info("已清空会话记忆: {}", sessionId);
    }

    /**
     * 提取摘要
     */
    private String extractSummary(String userInput, String aiResponse) {
        if (userInput.length() > 30) {
            return userInput.substring(0, 30) + "...";
        }
        return userInput;
    }

    /**
     * 计算重要性
     */
    private int calculateImportance(Map<String, Object> changes) {
        int importance = 1;
        if (changes != null) {
            if (changes.containsKey("currentLocation")) importance += 1;
            if (changes.containsKey("playerTraits")) importance += 1;
            if (changes.containsKey("inventory")) importance += 1;
            if (changes.containsKey("foreshadowing")) importance += 2;
        }
        return Math.min(importance, 5);
    }
}