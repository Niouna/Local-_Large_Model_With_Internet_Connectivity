package cn.edu.wtc.controller;



import com.fasterxml.jackson.core.type.TypeReference;
import cn.edu.wtc.game.entity.*;
import cn.edu.wtc.game.model.CharacterState;
import cn.edu.wtc.game.model.GameState;
import cn.edu.wtc.game.model.GameStateDelta;
import cn.edu.wtc.game.repository.CharacterTraitDefinitionRepository;
import cn.edu.wtc.game.repository.GameRecoveryPointRepository;
import cn.edu.wtc.game.repository.GameStateSnapshotRepository;
import cn.edu.wtc.game.repository.GameWorldProfileRepository;
import cn.edu.wtc.game.service.GameNarrativeMemoryService;
import cn.edu.wtc.game.service.GameStateManager;
import cn.edu.wtc.memory.service.MemoryService;
import cn.edu.wtc.ollama.service.ChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;


import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/v1/game")
public class GameStreamController {

    private static final Logger log = LoggerFactory.getLogger(GameStreamController.class);

    private final GameStateManager gameStateManager;
    private final ChatService chatService;
    private final MemoryService memoryService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    // 双模型配置
    @Value("${game.model.small:qwen2.5:3b}")
    private String smallModel;

    @Value("${game.model.large:deepseek-r1:7b}")
    private String largeModel;

    @Autowired
    private GameNarrativeMemoryService narrativeMemoryService;

    @Autowired
    public GameStreamController(GameStateManager gameStateManager,
                                ChatService chatService,
                                MemoryService memoryService) {
        this.gameStateManager = gameStateManager;
        this.chatService = chatService;
        this.memoryService = memoryService;
    }

    @Autowired
    private GameRecoveryPointRepository recoveryPointRepository;
    @Autowired
    private GameWorldProfileRepository profileRepository;
    @Autowired
    private GameStateSnapshotRepository snapshotRepository;
    @Autowired
    private CharacterTraitDefinitionRepository traitRepository;

    /**
     * 获取游戏状态
     */
    @GetMapping("/state/{sessionId}")
    public Map<String, Object> getState(@PathVariable String sessionId) {
        Map<String, Object> result = new HashMap<>();
        GameState state = gameStateManager.getState(sessionId);

        // 尝试从档案表获取静态数据
        Optional<GameWorldProfile> profileOpt = profileRepository.findBySessionId(sessionId);
        if (profileOpt.isPresent()) {
            GameWorldProfile profile = profileOpt.get();
            result.put("worldName", profile.getWorldName());
            result.put("worldBackground", profile.getWorldBackground());
            result.put("storyHook", profile.getStoryHook());
            result.put("playerName", profile.getPlayerName());
            result.put("playerDescription", profile.getPlayerDescription());
            result.put("playerTraits", parseJsonArray(profile.getPlayerTraits()));
            result.put("npcs", parseNpcsJson(profile.getNpcs()));
        } else {
            // 回退到从快照解析（兼容旧数据）
            result.put("worldName", state.getWorld().getWorldName());
            result.put("worldBackground", state.getWorld().getWorldBackground());
            result.put("storyHook", state.getWorld().getStoryHook());
            result.put("playerName", state.getCharacters().getPlayer().getName());
            result.put("playerDescription", state.getCharacters().getPlayer().getDescription());
            result.put("playerTraits", state.getCharacters().getPlayer().getTraits());
            // 构建 npcs 列表
            List<Map<String, Object>> npcList = new ArrayList<>();
            for (Map.Entry<String, CharacterState.NPC> entry : state.getCharacters().getNpcs().entrySet()) {
                CharacterState.NPC npc = entry.getValue();
                Map<String, Object> npcInfo = new HashMap<>();
                npcInfo.put("id", entry.getKey());
                npcInfo.put("name", npc.getName());
                npcInfo.put("role", npc.getRole());
                npcInfo.put("description", npc.getDescription());
                npcInfo.put("personality", npc.getPersonality());
                npcInfo.put("motivation", npc.getMotivation());
                npcInfo.put("traits", npc.getTraits());
                npcInfo.put("relationship", npc.getRelationship());
                npcList.add(npcInfo);
            }
            result.put("npcs", npcList);
        }

        // 动态数据
        result.put("sessionId", state.getSessionId());
        result.put("turnNumber", state.getTurnNumber());
        result.put("currentLocation", state.getWorld().getCurrentLocation());
        result.put("inventory", state.getCharacters().getPlayer().getInventory());
        result.put("activeForeshadowing", state.getPlot().getActiveForeshadowing());

        return result;
    }

    private List<String> parseJsonArray(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("解析 JSON 数组失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private List<Map<String, Object>> parseNpcsJson(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            log.warn("解析 NPC JSON 失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 流式生成剧情（双模型架构）
     * 小模型：分析意图和提取关键信息
     * 大模型：生成丰富的剧情和选项
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamAction(@RequestBody Map<String, Object> request) {
        String sessionId = (String) request.get("sessionId");
        String userInput = (String) request.get("userInput");
        String mode = (String) request.getOrDefault("mode", "NORMAL");
        String model = (String) request.getOrDefault("model", largeModel);
        long startTime = System.currentTimeMillis();

        log.info("游戏请求: session={}, input={}, mode={}, selectedModel={}", sessionId, userInput, mode, model);

        SseEmitter emitter = new SseEmitter(120000L);

        // 先发送一个"正在思考"的提示
        try {
            Map<String, Object> thinkingChunk = new HashMap<>();
            thinkingChunk.put("thinking", true);
            emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(thinkingChunk)));
        } catch (IOException e) {
            log.error("发送思考提示失败", e);
        }

        executor.execute(() -> {
            try {
                GameState state = gameStateManager.getState(sessionId);

                // ========== 第一步：小模型分析意图 ==========
                String intentResult = analyzeIntentWithSmallModel(state, userInput);
                log.info("小模型分析结果: {}", intentResult);

                // ========== 第二步：大模型生成剧情 ==========
                String prompt = buildGamePromptWithIntent(state, userInput, mode, intentResult);
                log.info("大模型 Prompt 长度: {}", prompt.length());

                // 调用大模型生成完整响应
                String fullResponse = chatService.chat(model, prompt);
                log.info("大模型响应长度: {}", fullResponse.length());

                // ========== 第三步：解析并发送响应 ==========
                parseAndSendResponse(fullResponse, state, emitter, userInput, mode, startTime);

            } catch (Exception e) {
                log.error("流式生成失败", e);
                try {
                    Map<String, Object> errorChunk = new HashMap<>();
                    errorChunk.put("error", "生成失败: " + e.getMessage());
                    emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(errorChunk)));
                } catch (IOException ex) {
                    log.error("发送错误失败", ex);
                }
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    /**
     * 用小模型分析用户意图（借鉴三级记忆的L1逻辑）
     */
    private String analyzeIntentWithSmallModel(GameState state, String userInput) {
        String prompt = String.format(
                "你是一个游戏系统的意图分析模块。你的任务是快速准确地从玩家输入中提取关键信息,分析玩家的输入，输出JSON格式的分析结果。\n\n" +
                        "当前游戏状态：\n" +
                        "- 位置：%s\n" +
                        "- 剧情节点：%s\n" +
                        "- 活跃伏笔：%s\n" +
                        "- 玩家特质：%s\n\n" +  // 改为特质
                        "玩家输入：%s\n\n" +
                        "请输出JSON（只输出JSON，不要其他内容）：\n" +
                        "{\n" +
                        "  \"intent\": \"玩家意图（explore/combat/dialogue/rest/ask/other）\",\n" +
                        "  \"intent_zh\": \"意图中文描述\",\n" +
                        "  \"location_change\": \"是否移动到了新地点（yes/no）\",\n" +
                        "  \"new_location\": \"如果是，新地点名称\",\n" +
                        "  \"key_items\": [\"提到的关键物品\"],\n" +
                        "  \"mentioned_npc\": \"提到的NPC名称\",\n" +
                        "  \"emotional_tone\": \"情绪基调（positive/negative/neutral）\",\n" +
                        "  \"should_advance_plot\": \"是否推进主线剧情（yes/no）\",\n" +
                        "  \"summary\": \"一句话总结玩家行动\"\n" +
                        "}",
                state.getWorld().getCurrentLocation(),
                state.getPlot().getCurrentNodeId(),
                state.getPlot().getActiveForeshadowing(),
                String.join("、", state.getCharacters().getPlayer().getTraits()),  // 显示特质
                userInput
        );

        try {
            String response = chatService.chat(smallModel, prompt);
            // 提取 JSON
            int start = response.indexOf('{');
            int end = response.lastIndexOf('}');
            if (start >= 0 && end > start) {
                String jsonStr = response.substring(start, end + 1);
                // 清理可能的 markdown
                jsonStr = jsonStr.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
                return jsonStr;
            }
            return "{}";
        } catch (Exception e) {
            log.error("小模型分析失败", e);
            return "{}";
        }
    }

    /**
     * 构建大模型 Prompt（包含小模型分析结果）
     */
    private String buildGamePromptWithIntent(GameState state, String userInput, String mode, String intentResult) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("【系统指令】你是一个优秀的互动小说作家，擅长创作沉浸式的叙事体验。\n\n");

        // 添加特质解释
        String traitExplanation = buildTraitExplanation(state);
        if (!traitExplanation.isEmpty()) {
            prompt.append(traitExplanation).append("\n");
        }

        // 世界观
        prompt.append("【世界观设定】\n");
        String worldBackground = state.getWorld().getWorldBackground();
        if (worldBackground != null && !worldBackground.isEmpty()) {
            prompt.append(worldBackground).append("\n\n");
        } else {
            prompt.append("（尚未设定世界背景）\n\n");
        }

        // 故事引子
        String storyHook = state.getWorld().getStoryHook();
        if (storyHook != null && !storyHook.isEmpty()) {
            prompt.append("【故事开端】\n").append(storyHook).append("\n\n");
        }

        // 玩家角色信息
        prompt.append("【玩家角色】\n");
        prompt.append("姓名：").append(state.getCharacters().getPlayer().getName()).append("\n");

        String playerDesc = state.getCharacters().getPlayer().getDescription();
        if (playerDesc != null && !playerDesc.isEmpty()) {
            prompt.append("背景：").append(playerDesc).append("\n");
        }

        List<String> playerTraits = state.getCharacters().getPlayer().getTraits();
        if (playerTraits != null && !playerTraits.isEmpty()) {
            prompt.append("性格特质：").append(String.join("、", playerTraits)).append("\n");
        }

        prompt.append("背包：").append(state.getCharacters().getPlayer().getInventory()).append("\n\n");

        // NPC信息
        if (!state.getCharacters().getNpcs().isEmpty()) {
            prompt.append("【重要NPC】\n");
            for (Map.Entry<String, CharacterState.NPC> entry : state.getCharacters().getNpcs().entrySet()) {
                CharacterState.NPC npc = entry.getValue();
                prompt.append("- ").append(npc.getName());
                if (npc.getRole() != null && !npc.getRole().isEmpty()) {
                    prompt.append("（").append(npc.getRole()).append("）");
                }
                prompt.append("\n");

                if (npc.getDescription() != null && !npc.getDescription().isEmpty()) {
                    prompt.append("  · 外貌/背景：").append(npc.getDescription().substring(0, Math.min(100, npc.getDescription().length()))).append("...\n");
                }
                if (npc.getPersonality() != null && !npc.getPersonality().isEmpty()) {
                    prompt.append("  · 性格：").append(npc.getPersonality()).append("\n");
                }
                if (npc.getMotivation() != null && !npc.getMotivation().isEmpty()) {
                    prompt.append("  · 动机：").append(npc.getMotivation()).append("\n");
                }
                List<String> npcTraits = npc.getTraits();
                if (npcTraits != null && !npcTraits.isEmpty()) {
                    prompt.append("  · 特质：").append(String.join("、", npcTraits)).append("\n");
                }
                if (npc.getRelationship() != null && npc.getRelationship() != 0) {
                    prompt.append("  · 好感度：").append(npc.getRelationship()).append("\n");
                }
            }
            prompt.append("\n");
        }

        // 当前状态
        prompt.append("【当前状态】\n");
        prompt.append("位置：").append(state.getWorld().getCurrentLocation()).append("\n");
        prompt.append("剧情节点：").append(state.getPlot().getCurrentNodeId()).append("\n");
        if (!state.getPlot().getActiveForeshadowing().isEmpty()) {
            prompt.append("活跃伏笔：").append(state.getPlot().getActiveForeshadowing()).append("\n");
        }
        prompt.append("\n");

        // 获取小说记忆
        String recentMemories = narrativeMemoryService.getRecentMemoriesForPrompt(state.getSessionId(), 5);
        if (!recentMemories.isEmpty()) {
            prompt.append(recentMemories).append("\n");
        }

        String importantMemories = narrativeMemoryService.getImportantMemoriesForPrompt(state.getSessionId());
        if (!importantMemories.isEmpty()) {
            prompt.append(importantMemories).append("\n");
        }

        // 意图分析结果
        prompt.append("【玩家意图分析】\n").append(intentResult).append("\n\n");

        // 玩家输入
        prompt.append("【玩家行动】\n").append(userInput).append("\n\n");

        // 发展模式
        prompt.append("【剧情发展要求】\n");
        switch (mode) {
            case "SURPRISE":
                prompt.append("制造一个意想不到的转折。\n");
                break;
            case "FORESHADOW":
                prompt.append("回收一个之前埋下的伏笔。\n");
                break;
            case "TWIST":
                prompt.append("让事情向更复杂、更困难的方向发展。\n");
                break;
            case "WAVE":
                prompt.append("让剧情一波三折，增加挑战性和戏剧性。\n");
                break;
            case "NORMAL":
            default:
                prompt.append("自然推进剧情。\n");
                break;
        }
        prompt.append("\n");

        // 输出格式 - 完全移除 attributes
        prompt.append("【输出格式】\n");
        prompt.append("请输出JSON，包含以下字段：\n");
        prompt.append("{\n");
        prompt.append("  \"narrative\": \"详细的剧情描述（1000-2000字，生动、沉浸感强）\",\n");
        prompt.append("  \"options\": [\n");
        prompt.append("    {\"text\": \"选项描述\", \"action\": \"选项对应的动作\"},\n");
        prompt.append("    {\"text\": \"选项描述\", \"action\": \"选项对应的动作\"},\n");
        prompt.append("    {\"text\": \"选项描述\", \"action\": \"选项对应的动作\"}\n");
        prompt.append("  ],\n");
        prompt.append("  \"stateChanges\": {\n");
        prompt.append("    \"currentLocation\": \"新位置（如有变化）\",\n");
        prompt.append("    \"playerTraits\": {\"add\": [\"特质名称\"], \"remove\": [\"特质名称\"]},\n");
        prompt.append("    \"inventory\": {\"add\": [\"物品\"], \"remove\": [\"物品\"]},\n");
        prompt.append("    \"foreshadowing\": {\"add\": [\"新伏笔\"], \"resolve\": [\"已回收伏笔\"]},\n");
        prompt.append("    \"flags\": {\"flag_name\": true}\n");
        prompt.append("  }\n");
        prompt.append("}\n\n");
        prompt.append("重要提示：\n");
        prompt.append("1. narrative 要生动、有画面感，体现角色特质\n");
        prompt.append("2. 至少提供2-3个选项\n");
        prompt.append("3. stateChanges 中的字段都是可选的，没有变化可以不写\n");
        prompt.append("4. playerTraits 用于添加或移除玩家的性格特质\n");
        prompt.append("5. 只输出JSON，不要有任何其他文字\n");

        return prompt.toString();
    }

    /**
     * 解析并发送响应
     */
    @SuppressWarnings("unchecked")
    private void parseAndSendResponse(String fullResponse, GameState state, SseEmitter emitter,
                                      String userInput, String mode, long startTime) {
        try {
            log.info("完整响应: {}", fullResponse.length() > 500 ? fullResponse.substring(0, 500) + "..." : fullResponse);

            // 处理 DeepSeek 的 think 标签
            String jsonStr = fullResponse;
            if (fullResponse.contains("<think>")) {
                int thinkEnd = fullResponse.lastIndexOf("</think>");
                if (thinkEnd > 0) {
                    jsonStr = fullResponse.substring(thinkEnd + 8).trim();
                }
            }

            // 提取 JSON
            int start = jsonStr.indexOf('{');
            int end = jsonStr.lastIndexOf('}');
            if (start >= 0 && end > start) {
                jsonStr = jsonStr.substring(start, end + 1);
            }

            // 清理 markdown
            jsonStr = jsonStr.replaceAll("```json\\s*", "");
            jsonStr = jsonStr.replaceAll("```\\s*", "");
            jsonStr = jsonStr.trim();

            // 修复 JSON 末尾可能的不完整
            if (!jsonStr.endsWith("}")) {
                jsonStr = jsonStr + "}";
            }

            // 修复常见的 JSON 格式错误
            jsonStr = fixCommonJsonErrors(jsonStr);
            // 新增：修复字符串内部未转义的双引号
            jsonStr = fixJsonStringQuotes(jsonStr);
            // 在提取 JSON 字符串之后
            jsonStr = fixJsonStringQuotes(jsonStr);

            // 额外清理：修复空对象格式问题
            jsonStr = jsonStr.replaceAll("\"playerTraits\":\\s*\\{\\s*\\}", "\"playerTraits\": {}");
            jsonStr = jsonStr.replaceAll("\"inventory\":\\s*\\{\\s*\\}", "\"inventory\": {}");

            // 确保 foreshadowing 中的 add 和 resolve 是数组
            jsonStr = jsonStr.replaceAll(
                    "\"foreshadowing\":\\s*\\{\\s*\"add\":\\s*\"([^\"]+)\"\\s*\\}",
                    "\"foreshadowing\": {\"add\": [\"$1\"]}"
            );
            jsonStr = jsonStr.replaceAll(
                    "\"foreshadowing\":\\s*\\{\\s*\"resolve\":\\s*\"([^\"]+)\"\\s*\\}",
                    "\"foreshadowing\": {\"resolve\": [\"$1\"]}"
            );

            log.info("解析 JSON: {}", jsonStr.length() > 200 ? jsonStr.substring(0, 200) + "..." : jsonStr);

            Map<String, Object> parsed = objectMapper.readValue(jsonStr, Map.class);

            // 发送剧情
            String narrative = (String) parsed.get("narrative");
            if (narrative != null && !narrative.isEmpty()) {
                Map<String, Object> narrativeChunk = new HashMap<>();
                narrativeChunk.put("narrative", narrative);
                emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(narrativeChunk)));
            }

            // 发送选项
            Object options = parsed.get("options");
            if (options != null) {
                Map<String, Object> optionsChunk = new HashMap<>();
                optionsChunk.put("options", options);
                emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(optionsChunk)));
            }

            // 发送状态变更并持久化
            Object stateChanges = parsed.get("stateChanges");
            if (stateChanges != null) {
                stateChanges = fixStateChanges(stateChanges);

                Map<String, Object> stateChunk = new HashMap<>();
                stateChunk.put("stateChanges", stateChanges);
                emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(stateChunk)));

                // ========== 持久化到数据库 ==========
                GameStateDelta delta = buildDeltaFromChanges(stateChanges);
                if (delta != null) {
                    GameEventLog event = new GameEventLog();
                    event.setSessionId(state.getSessionId());
                    event.setEventType("GAME_ACTION");
                    event.setUserInput(userInput);
                    event.setNarrativeMode(mode);
                    event.setAiResponse(narrative != null ? narrative : "");
                    event.setProcessTimeMs((int)(System.currentTimeMillis() - startTime));
                    event.setModelUsed(largeModel);
                    event.setEventTime(LocalDateTime.now());

                    // 持久化（会自动创建快照）
                    gameStateManager.updateState(state.getSessionId(), delta, event);
                    log.info("游戏状态已持久化: session={}, turn={}", state.getSessionId(), state.getTurnNumber() + 1);
                }

                // 更新内存状态
                updateGameState(state, stateChanges);
            }

            // 异步记录到小说记忆系统
            final String finalSessionId = state.getSessionId();
            final int finalTurnNumber = state.getTurnNumber() + 1;
            final String finalLocation = state.getWorld().getCurrentLocation();
            final String finalUserInput = userInput;
            final String finalNarrative = narrative;
            final Map<String, Object> finalChanges = stateChanges != null ? (Map<String, Object>) stateChanges : null;

            executor.submit(() -> {
                try {
                    narrativeMemoryService.recordPlot(
                            finalSessionId, finalTurnNumber,
                            finalUserInput, finalNarrative,
                            finalLocation, finalChanges
                    );
                } catch (Exception e) {
                    log.error("小说记忆记录失败", e);
                }
            });

            emitter.complete();

        } catch (Exception e) {
            log.error("解析响应失败", e);
            try {
                Map<String, Object> errorChunk = new HashMap<>();
                errorChunk.put("error", "解析失败: " + e.getMessage());
                emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(errorChunk)));
            } catch (IOException ex) {
                log.error("发送错误失败", ex);
            }
            emitter.complete();
        }
    }

    /**
     * 构建角色特质解释文本
     */
    private String buildTraitExplanation(GameState state) {
        // 收集所有角色特质
        Set<String> allTraits = new HashSet<>();

        // 玩家特质
        allTraits.addAll(state.getCharacters().getPlayer().getTraits());

        // NPC特质
        for (CharacterState.NPC npc : state.getCharacters().getNpcs().values()) {
            allTraits.addAll(npc.getTraits());
        }

        if (allTraits.isEmpty()) {
            return "";
        }

        // 从数据库查询特质解释
        List<CharacterTraitDefinition> definitions = traitRepository.findByTraitNameIn(new ArrayList<>(allTraits));

        if (definitions.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder("\n【角色特质解释】\n");
        sb.append("以下是当前游戏中角色性格特质的详细解释，请根据这些理解来塑造角色行为：\n\n");

        for (CharacterTraitDefinition def : definitions) {
            sb.append("- **").append(def.getTraitName()).append("**：");
            sb.append(def.getDescription());
            if (def.getExample() != null && !def.getExample().isEmpty()) {
                sb.append("（例如：").append(def.getExample()).append("）");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * 构建游戏 Prompt（保留原有方法，供内部调用）
     */
    private String buildGamePrompt(GameState state, String userInput, String mode) {
        return buildGamePromptWithIntent(state, userInput, mode, "{}");
    }

    /**
     * 修复常见的 JSON 格式错误
     */
    /**
     * 修复常见的 JSON 格式错误
     */
    private String fixCommonJsonErrors(String json) {
        // 1. 修复中文引号
        json = json.replace("“", "\"")
                .replace("”", "\"")
                .replace("‘", "'")
                .replace("’", "'");

        // 2. 修复 foreshadowing: {"某个文本"} -> foreshadowing: {"add": ["某个文本"]}
        json = json.replaceAll(
                "\"foreshadowing\":\\s*\\{\"([^\"]+)\"\\}",
                "\"foreshadowing\": {\"add\": [\"$1\"]}"
        );

        // 3. 修复 foreshadowing: {"add": "文本"} -> foreshadowing: {"add": ["文本"]}
        json = json.replaceAll(
                "\"foreshadowing\":\\s*\\{\"add\":\\s*\"([^\"]+)\"\\}",
                "\"foreshadowing\": {\"add\": [\"$1\"]}"
        );

        // 4. 修复 foreshadowing: {"resolve": "文本"} -> foreshadowing: {"resolve": ["文本"]}
        json = json.replaceAll(
                "\"foreshadowing\":\\s*\\{\"resolve\":\\s*\"([^\"]+)\"\\}",
                "\"foreshadowing\": {\"resolve\": [\"$1\"]}"
        );

        // 5. 修复 inventory: {"add": "文本"} -> inventory: {"add": ["文本"]}
        json = json.replaceAll(
                "\"inventory\":\\s*\\{\"add\":\\s*\"([^\"]+)\"\\}",
                "\"inventory\": {\"add\": [\"$1\"]}"
        );

        // 6. 修复 playerTraits: {"add": "文本"} -> playerTraits: {"add": ["文本"]}
        json = json.replaceAll(
                "\"playerTraits\":\\s*\\{\"add\":\\s*\"([^\"]+)\"\\}",
                "\"playerTraits\": {\"add\": [\"$1\"]}"
        );

        // 7. 修复 playerTraits: {"remove": "文本"} -> playerTraits: {"remove": ["文本"]}
        json = json.replaceAll(
                "\"playerTraits\":\\s*\\{\"remove\":\\s*\"([^\"]+)\"\\}",
                "\"playerTraits\": {\"remove\": [\"$1\"]}"
        );

        // 8. 关键修复：转义 JSON 字符串中的双引号（处理中文内容中的引号）
        // 匹配 JSON 字符串值中的引号并转义
        json = escapeQuotesInJsonValues(json);

        return json;
    }

    /**
     * 转义 JSON 字符串值中的双引号
     */
    private String escapeQuotesInJsonValues(String json) {
        StringBuilder result = new StringBuilder();
        boolean inString = false;
        boolean escapeNext = false;
        char prevChar = 0;

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);

            if (escapeNext) {
                result.append(c);
                escapeNext = false;
                continue;
            }

            if (c == '\\') {
                escapeNext = true;
                result.append(c);
                continue;
            }

            if (c == '"') {
                // 检查是否是字符串的开始/结束
                if (i > 0 && prevChar != '\\') {
                    inString = !inString;
                }
                result.append(c);
            } else if (inString && c == '"') {
                // 在字符串内部遇到双引号，需要转义
                result.append('\\');
                result.append(c);
            } else {
                result.append(c);
            }

            prevChar = c;
        }

        return result.toString();
    }

    /**
     * 更强大的 JSON 修复：处理字符串值内部的未转义双引号
     */
    private String fixJsonStringQuotes(String json) {
        StringBuilder result = new StringBuilder();
        boolean inString = false;
        boolean escape = false;
        int i = 0;
        int len = json.length();

        while (i < len) {
            char c = json.charAt(i);

            if (escape) {
                result.append(c);
                escape = false;
                i++;
                continue;
            }

            if (c == '\\') {
                escape = true;
                result.append(c);
                i++;
                continue;
            }

            if (c == '"') {
                // 检查这个引号是否真的是字符串边界
                if (!inString) {
                    // 进入字符串
                    inString = true;
                    result.append(c);
                } else {
                    // 在字符串内部，需要判断是否是真正的结束符
                    // 简单规则：如果下一个字符是 : , } ] 或空白，则视为结束
                    boolean isEnd = false;
                    int nextIdx = i + 1;
                    if (nextIdx < len) {
                        char next = json.charAt(nextIdx);
                        if (next == ':' || next == ',' || next == '}' || next == ']' || Character.isWhitespace(next)) {
                            isEnd = true;
                        }
                    } else {
                        isEnd = true;
                    }

                    if (isEnd) {
                        inString = false;
                        result.append(c);
                    } else {
                        // 不是结束，则转义
                        result.append('\\').append(c);
                    }
                }
                i++;
                continue;
            }

            result.append(c);
            i++;
        }

        return result.toString();
    }

    /**
     * 从 stateChanges 构建 GameStateDelta
     */
    @SuppressWarnings("unchecked")
    private GameStateDelta buildDeltaFromChanges(Object stateChangesObj) {
        if (stateChangesObj == null) {
            return null;
        }

        Map<String, Object> changes = (Map<String, Object>) stateChangesObj;
        GameStateDelta delta = new GameStateDelta();

        // 世界观变化
        String newLocation = (String) changes.get("currentLocation");
        Map<String, Boolean> flags = (Map<String, Boolean>) changes.get("flags");
        String worldBackground = (String) changes.get("worldBackground");

        if (newLocation != null || (flags != null && !flags.isEmpty()) || worldBackground != null) {
            GameStateDelta.WorldStateDelta worldDelta = new GameStateDelta.WorldStateDelta();
            if (newLocation != null) {
                worldDelta.setCurrentLocation(newLocation);
            }
            if (flags != null) {
                Map<String, Object> flagMap = new HashMap<>();
                flagMap.putAll(flags);
                worldDelta.setFlagsToSet(flagMap);
            }
            if (worldBackground != null) {
                worldDelta.setWorldBackground(worldBackground);
            }
            delta.setWorldDelta(worldDelta);
        }

        // 角色变化
        Map<String, Object> playerTraitsChanges = (Map<String, Object>) changes.get("playerTraits");
        Map<String, Object> inventoryChanges = (Map<String, Object>) changes.get("inventory");
        Map<String, Object> npcTraitsChanges = (Map<String, Object>) changes.get("npcTraits");

        if ((playerTraitsChanges != null && !playerTraitsChanges.isEmpty()) ||
                (inventoryChanges != null && !inventoryChanges.isEmpty()) ||
                (npcTraitsChanges != null && !npcTraitsChanges.isEmpty())) {

            GameStateDelta.CharacterStateDelta charDelta = new GameStateDelta.CharacterStateDelta();

            // 处理玩家特质变化
            if (playerTraitsChanges != null) {
                List<String> addTraits = (List<String>) playerTraitsChanges.get("add");
                List<String> removeTraits = (List<String>) playerTraitsChanges.get("remove");
                if (addTraits != null && !addTraits.isEmpty()) {
                    charDelta.setPlayerTraitsToAdd(addTraits);
                }
                if (removeTraits != null && !removeTraits.isEmpty()) {
                    charDelta.setPlayerTraitsToRemove(removeTraits);
                }
            }

            // 处理背包变化
            if (inventoryChanges != null) {
                List<String> addItems = (List<String>) inventoryChanges.get("add");
                List<String> removeItems = (List<String>) inventoryChanges.get("remove");
                if (addItems != null && !addItems.isEmpty()) {
                    charDelta.setItemsAdded(addItems);
                }
                if (removeItems != null && !removeItems.isEmpty()) {
                    charDelta.setItemsRemoved(removeItems);
                }
            }

            // 处理NPC特质变化
            if (npcTraitsChanges != null && !npcTraitsChanges.isEmpty()) {
                Map<String, List<String>> npcUpdates = new HashMap<>();
                for (Map.Entry<String, Object> entry : npcTraitsChanges.entrySet()) {
                    String npcId = entry.getKey();
                    Object value = entry.getValue();
                    if (value instanceof Map) {
                        Map<String, Object> npcChange = (Map<String, Object>) value;
                        List<String> addTraits = (List<String>) npcChange.get("add");
                        if (addTraits != null && !addTraits.isEmpty()) {
                            npcUpdates.put(npcId, addTraits);
                        }
                    } else if (value instanceof List) {
                        npcUpdates.put(npcId, (List<String>) value);
                    }
                }
                if (!npcUpdates.isEmpty()) {
                    charDelta.setNpcTraitsUpdates(npcUpdates);
                }
            }

            delta.setCharacterDelta(charDelta);
        }

        // 剧情变化
        Map<String, Object> foreshadowingChanges = (Map<String, Object>) changes.get("foreshadowing");
        String newNodeId = (String) changes.get("currentNodeId");
        Map<String, Boolean> plotFlags = (Map<String, Boolean>) changes.get("plotFlags");

        if (foreshadowingChanges != null || newNodeId != null || (plotFlags != null && !plotFlags.isEmpty())) {
            GameStateDelta.PlotStateDelta plotDelta = new GameStateDelta.PlotStateDelta();

            if (newNodeId != null) {
                plotDelta.setCurrentNodeId(newNodeId);
            }
            if (plotFlags != null && !plotFlags.isEmpty()) {
                plotDelta.setFlagsToSet(plotFlags);
            }
            if (foreshadowingChanges != null) {
                List<String> add = (List<String>) foreshadowingChanges.get("add");
                List<String> resolve = (List<String>) foreshadowingChanges.get("resolve");
                if (add != null && !add.isEmpty()) {
                    plotDelta.setForeshadowingAdded(add);
                }
                if (resolve != null && !resolve.isEmpty()) {
                    plotDelta.setForeshadowingResolved(resolve);
                }
            }

            delta.setPlotDelta(plotDelta);
        }

        // 检查是否有任何变化
        if (delta.getWorldDelta() == null && delta.getCharacterDelta() == null && delta.getPlotDelta() == null) {
            return null;
        }

        return delta;
    }
    /**
     * 修复 stateChanges 中的格式问题
     */
    @SuppressWarnings("unchecked")
    private Object fixStateChanges(Object stateChanges) {
        if (!(stateChanges instanceof Map)) {
            return stateChanges;
        }

        Map<String, Object> changes = (Map<String, Object>) stateChanges;

        // 修复 foreshadowing 字段
        Object foreshadowing = changes.get("foreshadowing");
        if (foreshadowing instanceof Map) {
            Map<String, Object> fMap = (Map<String, Object>) foreshadowing;
            if (fMap.size() == 1 && !fMap.containsKey("add") && !fMap.containsKey("resolve")) {
                String key = fMap.keySet().iterator().next();
                Object value = fMap.get(key);
                Map<String, Object> fixed = new HashMap<>();
                if (value instanceof String) {
                    fixed.put("add", java.util.Collections.singletonList(value));
                } else if (value instanceof List) {
                    fixed.put("add", value);
                }
                changes.put("foreshadowing", fixed);
            }
        }

        // 修复 inventory 字段
        Object inventory = changes.get("inventory");
        if (inventory instanceof Map) {
            Map<String, Object> iMap = (Map<String, Object>) inventory;
            if (iMap.size() == 1 && !iMap.containsKey("add") && !iMap.containsKey("remove")) {
                String key = iMap.keySet().iterator().next();
                Object value = iMap.get(key);
                Map<String, Object> fixed = new HashMap<>();
                if (value instanceof String) {
                    fixed.put("add", java.util.Collections.singletonList(value));
                } else if (value instanceof List) {
                    fixed.put("add", value);
                }
                changes.put("inventory", fixed);
            }
        }

        return changes;
    }

    /**
     * 更新游戏状态
     */
    @SuppressWarnings("unchecked")
    private void updateGameState(GameState state, Object stateChangesObj) {
        try {
            Map<String, Object> changes = (Map<String, Object>) stateChangesObj;

            // 更新位置
            String newLocation = (String) changes.get("currentLocation");
            if (newLocation != null && !newLocation.isEmpty()) {
                state.getWorld().setCurrentLocation(newLocation);
            }

            // 更新玩家特质（替代之前的属性更新）
            Map<String, Object> playerTraitsChanges = (Map<String, Object>) changes.get("playerTraits");
            if (playerTraitsChanges != null) {
                List<String> addTraits = (List<String>) playerTraitsChanges.get("add");
                List<String> removeTraits = (List<String>) playerTraitsChanges.get("remove");

                if (addTraits != null) {
                    state.getCharacters().getPlayer().getTraits().addAll(addTraits);
                }
                if (removeTraits != null) {
                    state.getCharacters().getPlayer().getTraits().removeAll(removeTraits);
                }
            }

            // 更新背包
            Map<String, Object> inventoryChanges = (Map<String, Object>) changes.get("inventory");
            if (inventoryChanges != null) {
                List<String> addItems = (List<String>) inventoryChanges.get("add");
                List<String> removeItems = (List<String>) inventoryChanges.get("remove");

                if (addItems != null) {
                    state.getCharacters().getPlayer().getInventory().addAll(addItems);
                }
                if (removeItems != null) {
                    state.getCharacters().getPlayer().getInventory().removeAll(removeItems);
                }
            }

            // 更新伏笔
            Map<String, Object> foreshadowingChanges = (Map<String, Object>) changes.get("foreshadowing");
            if (foreshadowingChanges != null) {
                List<String> add = (List<String>) foreshadowingChanges.get("add");
                List<String> resolve = (List<String>) foreshadowingChanges.get("resolve");

                if (add != null) {
                    state.getPlot().getActiveForeshadowing().addAll(add);
                }
                if (resolve != null) {
                    state.getPlot().getActiveForeshadowing().removeAll(resolve);
                    state.getPlot().getResolvedForeshadowing().addAll(resolve);
                }
            }

            // 更新剧情节点
            String newNodeId = (String) changes.get("currentNodeId");
            if (newNodeId != null) {
                state.getPlot().setCurrentNodeId(newNodeId);
                if (!state.getPlot().getVisitedNodes().contains(newNodeId)) {
                    state.getPlot().getVisitedNodes().add(newNodeId);
                }
            }

            // 更新标记
            Map<String, Boolean> flags = (Map<String, Boolean>) changes.get("flags");
            if (flags != null) {
                state.getPlot().getFlags().putAll(flags);
            }

            // 增加轮数
            state.setTurnNumber(state.getTurnNumber() + 1);

            log.info("游戏状态已更新: 轮数={}, 位置={}, 特质={}, 伏笔数={}",
                    state.getTurnNumber(),
                    state.getWorld().getCurrentLocation(),
                    state.getCharacters().getPlayer().getTraits(),
                    state.getPlot().getActiveForeshadowing().size());

        } catch (Exception e) {
            log.error("更新游戏状态失败", e);
        }
    }

//    /**
//     * 保存恢复点
//     */
//    @PostMapping("/recovery-point")
//    public Map<String, Object> saveRecoveryPoint(@RequestBody Map<String, String> request) {
//        String sessionId = request.get("sessionId");
//        String pointName = request.getOrDefault("pointName", "手动保存");
//
//        log.info("保存恢复点: session={}, name={}", sessionId, pointName);
//
//        Map<String, Object> result = new HashMap<>();
//        result.put("status", "ok");
//        result.put("message", "恢复点已保存");
//        return result;
//    }

    /**
     * 重置游戏
     */
    @PostMapping("/reset/{sessionId}")
    public Map<String, Object> resetGame(@PathVariable String sessionId) {
        gameStateManager.clearCache(sessionId);
        narrativeMemoryService.clearSession(sessionId);

        Map<String, Object> result = new HashMap<>();
        result.put("status", "ok");
        result.put("message", "游戏已重置");
        return result;
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "ok");
        result.put("service", "game-module");
        result.put("timestamp", System.currentTimeMillis());
        return result;
    }


    /**
     * 保存恢复点
     */
    @PostMapping("/recovery-point")
    public Map<String, Object> saveRecoveryPoint(@RequestBody Map<String, String> request) {
        String sessionId = request.get("sessionId");
        String pointName = request.getOrDefault("pointName", "手动保存");
        String description = request.getOrDefault("description", "");

        log.info("保存恢复点: session={}, name={}", sessionId, pointName);

        Map<String, Object> result = new HashMap<>();

        try {
            // 获取当前游戏状态（从内存）
            GameState currentState = gameStateManager.getState(sessionId);

            // 强制创建当前状态的快照（用于存档）
            GameStateSnapshot snapshot = new GameStateSnapshot();
            snapshot.setSessionId(sessionId);
            snapshot.setWorldState(objectMapper.writeValueAsString(currentState.getWorld()));
            snapshot.setCharacterState(objectMapper.writeValueAsString(currentState.getCharacters()));
            snapshot.setPlotState(objectMapper.writeValueAsString(currentState.getPlot()));
            snapshot.setNarrativeMemory(currentState.getNarrativeSummary());
            snapshot.setVersion(currentState.getVersion());
            snapshot.setTurnNumber(currentState.getTurnNumber());
            snapshot.setIsCurrent(true);
            snapshot.setSnapshotTime(LocalDateTime.now());

            // 保存快照
            snapshot = snapshotRepository.save(snapshot);
            log.info("已创建存档快照: id={}, turn={}", snapshot.getId(), snapshot.getTurnNumber());

            // 创建恢复点
            GameRecoveryPoint recoveryPoint = new GameRecoveryPoint();
            recoveryPoint.setSessionId(sessionId);
            recoveryPoint.setPointName(pointName);
            recoveryPoint.setDescription(description);
            recoveryPoint.setStateSnapshotId(snapshot.getId());
            recoveryPoint.setTurnNumber(currentState.getTurnNumber());
            recoveryPoint.setIsAuto(pointName.contains("自动保存") || pointName.startsWith("存档_"));

            recoveryPointRepository.save(recoveryPoint);
            log.info("恢复点已保存: id={}, name={}", recoveryPoint.getId(), pointName);

            result.put("status", "ok");
            result.put("message", "✅ 恢复点已保存: " + pointName);
            result.put("recoveryPointId", recoveryPoint.getId());

        } catch (Exception e) {
            log.error("保存恢复点失败", e);
            result.put("status", "error");
            result.put("message", "保存失败: " + e.getMessage());
        }

        return result;
    }

    /**
     * 获取会话的所有恢复点
     */
    @GetMapping("/recovery-points/{sessionId}")
    public Map<String, Object> getRecoveryPoints(@PathVariable String sessionId) {
        List<GameRecoveryPoint> points = recoveryPointRepository
                .findBySessionIdOrderByCreatedAtDesc(sessionId);

        Map<String, Object> result = new HashMap<>();
        result.put("status", "ok");
        result.put("points", points);
        return result;
    }

    /**
     * 恢复到指定恢复点
     */
    @PostMapping("/restore/{recoveryPointId}")
    public Map<String, Object> restoreToPoint(@PathVariable Long recoveryPointId) {
        Map<String, Object> result = new HashMap<>();

        try {
            GameRecoveryPoint recoveryPoint = recoveryPointRepository
                    .findById(recoveryPointId)
                    .orElse(null);

            if (recoveryPoint == null) {
                result.put("status", "error");
                result.put("message", "恢复点不存在");
                return result;
            }

            GameStateSnapshot snapshot = snapshotRepository
                    .findById(recoveryPoint.getStateSnapshotId())
                    .orElse(null);

            if (snapshot == null) {
                result.put("status", "error");
                result.put("message", "快照不存在");
                return result;
            }

            // 将快照设为当前
            snapshotRepository.updateCurrentFlag(recoveryPoint.getSessionId());
            snapshot.setIsCurrent(true);
            snapshotRepository.save(snapshot);

            // 清除缓存，下次请求会重新加载
            gameStateManager.clearCache(recoveryPoint.getSessionId());

            result.put("status", "ok");
            result.put("message", "已恢复到: " + recoveryPoint.getPointName());
            result.put("turnNumber", snapshot.getTurnNumber());

        } catch (Exception e) {
            log.error("恢复失败", e);
            result.put("status", "error");
            result.put("message", "恢复失败: " + e.getMessage());
        }

        return result;
    }
}