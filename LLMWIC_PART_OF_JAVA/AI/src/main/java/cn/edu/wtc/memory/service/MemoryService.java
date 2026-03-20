package cn.edu.wtc.memory.service;

import cn.edu.wtc.memory.entity.ConversationMemory;
import cn.edu.wtc.memory.repository.ConversationMemoryRepository;
import cn.edu.wtc.ollama.service.ChatService; // 假设这是你现有的统一聊天服务
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class MemoryService {

    private static final Logger log = LoggerFactory.getLogger(MemoryService.class);

    // 模型名称常量
    private static final String MODEL_3B = "qwen2.5:3b";
    private static final String MODEL_DS = "deepseek-r1:7b"; // 或者你的 DS 模型名

    @Autowired
    private ConversationMemoryRepository memoryRepository;

    @Autowired
    private ChatService chatService; // 统一注入，内部根据 model 参数路由

    /**
     * 入口：添加 L1 记忆并触发升级检查
     */
    public void addL1Memory(String sessionId, String userMessage, String aiResponse) {
        if (sessionId == null) return;

        try {
            // 1. 调用 3B 生成 L1 总结
            String summary = generateL1Summary(userMessage, aiResponse);
            if (summary == null || summary.isBlank()) {
                log.warn("L1 总结为空，跳过保存。Session: {}", sessionId);
                return;
            }

            // 2. 保存 L1
            ConversationMemory l1 = new ConversationMemory(sessionId, 1, summary, null);
            memoryRepository.save(l1);
            log.debug("L1 记忆已保存 [ID:{}], Session: {}", l1.getId(), sessionId);

            // 3. 触发升级检查 (事务内执行)
            checkAndUpgrade(sessionId);

        } catch (Exception e) {
            log.error("添加 L1 记忆或升级过程中发生异常", e);
            // 不要抛出异常，避免影响主对话流程
        }
    }

    /**
     * 检查并执行升级逻辑 (L1->L2, L2->L3)
     */
    // 去掉 @Transactional
    public void checkAndUpgrade(String sessionId) {
        checkL1ToL2(sessionId); // 这个方法自己有 @Transactional
        checkL2ToL3(sessionId); // 这个方法自己有 @Transactional
    }

    @Transactional
    protected void checkL1ToL2(String sessionId) {
        // 阈值：5 条
        long count = memoryRepository.countBySessionIdAndLevelAndIsActiveTrue(sessionId, 1);
        if (count < 5) return;

        log.info("触发 L1->L2 升级，Session: {}", sessionId);

        // 取最早的 5 条
        List<ConversationMemory> toUpgrade = memoryRepository
                .findBySessionIdAndLevelAndIsActiveTrueOrderByCreatedAtAsc(sessionId, 1)
                .stream().limit(5).collect(Collectors.toList());

        if (toUpgrade.size() < 5) return; // 防御性检查

        List<Long> sourceIds = toUpgrade.stream().map(ConversationMemory::getId).collect(Collectors.toList());
        List<String> contents = toUpgrade.stream().map(ConversationMemory::getContent).collect(Collectors.toList());

        // 调用 DS 生成 L2
        String l2Content = generateL2Summary(contents);
        if (l2Content == null || l2Content.isBlank()) {
            log.warn("L2 生成失败，暂停升级");
            return;
        }

        // 保存 L2
        ConversationMemory l2 = new ConversationMemory(
                sessionId,
                2,
                l2Content,
                sourceIds.stream().map(String::valueOf).collect(Collectors.joining(","))
        );
        memoryRepository.save(l2);

        // 归档旧 L1
        memoryRepository.deactivateByIds(sourceIds);
        log.info("L1->L2 升级完成，新 L2 ID: {}", l2.getId());
    }

    @Transactional
    protected void checkL2ToL3(String sessionId) {
        // 阈值：3 条
        long count = memoryRepository.countBySessionIdAndLevelAndIsActiveTrue(sessionId, 2);
        if (count < 3) return;

        log.info("触发 L2->L3 升级，Session: {}", sessionId);

        // 取最早的 3 条 L2
        List<ConversationMemory> toUpgrade = memoryRepository
                .findBySessionIdAndLevelAndIsActiveTrueOrderByCreatedAtAsc(sessionId, 2)
                .stream().limit(3).collect(Collectors.toList());

        if (toUpgrade.size() < 3) return;

        List<Long> sourceIds = toUpgrade.stream().map(ConversationMemory::getId).collect(Collectors.toList());
        List<String> contents = toUpgrade.stream().map(ConversationMemory::getContent).collect(Collectors.toList());

        // 获取当前的 L3 (如果有)
        ConversationMemory currentL3 = memoryRepository.findFirstBySessionIdAndLevelAndIsActiveTrueOrderByCreatedAtDesc(sessionId, 3);
        String oldL3Content = (currentL3 != null) ? currentL3.getContent() : "暂无核心画像";

        // 调用 DS 生成/更新 L3
        String l3Content = generateL3Summary(contents, oldL3Content);
        if (l3Content == null || l3Content.isBlank()) {
            log.warn("L3 生成失败，暂停升级");
            return;
        }

        // 归档旧 L3 (如果有)
        if (currentL3 != null) {
            memoryRepository.deactivateByIds(List.of(currentL3.getId()));
        }

        // 保存新 L3
        ConversationMemory l3 = new ConversationMemory(
                sessionId,
                3,
                l3Content,
                sourceIds.stream().map(String::valueOf).collect(Collectors.joining(","))
        );
        memoryRepository.save(l3);

        // 归档旧 L2
        memoryRepository.deactivateByIds(sourceIds);
        log.info("L2->L3 升级完成，新 L3 ID: {}", l3.getId());
    }

    /**
     * 组装上下文 Prompt
     */
    public String assembleMemoryContext(String sessionId) {
        if (sessionId == null) return "";

        List<String> parts = new ArrayList<>();

        // L3
        ConversationMemory l3 = memoryRepository.findFirstBySessionIdAndLevelAndIsActiveTrueOrderByCreatedAtDesc(sessionId, 3);
        if (l3 != null) {
            parts.add("长期核心：" + l3.getContent());
        }

        // L2
        List<ConversationMemory> l2List = memoryRepository
                .findBySessionIdAndLevelAndIsActiveTrueOrderByCreatedAtDesc(sessionId, 2);
        if (!l2List.isEmpty()) {
            String l2Text = l2List.stream().limit(3)
                    .map(ConversationMemory::getContent)
                    .collect(Collectors.joining("；"));
            parts.add("中期逻辑：" + l2Text);
        }

        // L1
        List<ConversationMemory> l1List = memoryRepository
                .findBySessionIdAndLevelAndIsActiveTrueOrderByCreatedAtDesc(sessionId, 1);
        if (!l1List.isEmpty()) {
            String l1Text = l1List.stream().limit(5)
                    .map(ConversationMemory::getContent)
                    .collect(Collectors.joining("；"));
            parts.add("近期事实：" + l1Text);
        }

        if (parts.isEmpty()) return "";
        return String.join(" ", parts);
    }

    // --- Prompt 生成辅助方法 ---

    private String generateL1Summary(String userMsg, String aiResp) {
        String prompt = String.format(
                "请忽略寒暄，将以下对话总结为一句客观的事实性描述（保留关键数据、意图）。只输出总结内容，不要其他废话。\n用户：%s\nAI：%s\n总结：",
                userMsg, aiResp
        );
        try {
            return chatService.chat(MODEL_3B, prompt).trim();
        } catch (Exception e) {
            log.error("L1 总结生成失败", e);
            return null;
        }
    }

    private String generateL2Summary(List<String> l1Contents) {
        String input = l1Contents.stream().map(s -> "- " + s).collect(Collectors.joining("\n"));
        String prompt = String.format(
                "以下是 5 条近期的对话事实总结。请分析用户的深层意图、潜在需求或未解决的问题，提炼出一段逻辑性总结。\n事实列表:\n%s\n\n深层逻辑总结：",
                input
        );
        try {
            String raw = chatService.chat(MODEL_DS, prompt).trim();
            // 清洗 <think> 标签及其内容（跨行模式）
            String cleaned = raw.replaceAll("(?s)<think>.*?</think>", "").trim();
            return cleaned;
        } catch (Exception e) {
            log.error("L2 总结生成失败", e);
            return null;
        }
    }

    private String generateL3Summary(List<String> l2Contents, String oldL3) {
        String input = l2Contents.stream().map(s -> "- " + s).collect(Collectors.joining("\n"));
        String prompt = String.format(
                "现有核心画像：%s\n\n新增的 3 条中期逻辑总结:\n%s\n\n请融合新旧信息，更新用户的核心画像（包含核心目标、偏好、约束）。只输出更新后的画像内容：",
                oldL3, input
        );
        try {
            String raw = chatService.chat(MODEL_DS, prompt).trim();
            String cleaned = raw.replaceAll("(?s)<think>.*?</think>", "").trim();
            return cleaned;
        } catch (Exception e) {
            log.error("L3 总结生成失败", e);
            return null;
        }
    }
}