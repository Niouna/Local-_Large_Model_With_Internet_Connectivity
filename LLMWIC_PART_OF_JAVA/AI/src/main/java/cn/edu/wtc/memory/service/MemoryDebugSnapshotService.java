package cn.edu.wtc.memory.service;

import cn.edu.wtc.memory.entity.ConversationMemory;
import cn.edu.wtc.memory.repository.ConversationMemoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class MemoryDebugSnapshotService {

    private static final Logger log = LoggerFactory.getLogger(MemoryDebugSnapshotService.class);
    private static final String DEBUG_DIR = "./debug_snapshots";
    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private ConversationMemoryRepository repository;

    private LocalDateTime lastSnapshotTime; // 新增：最后一次快照时间

    // 每分钟执行一次
    @Scheduled(fixedRate = 60000)
    public void generateSnapshots() {
        log.debug("开始生成记忆调试快照...");

        List<ConversationMemory> allActive = repository.findAll();
        Set<String> sessionIds = allActive.stream()
                .filter(m -> m.getIsActive())
                .map(ConversationMemory::getSessionId)
                .collect(Collectors.toSet());

        for (String sessionId : sessionIds) {
            saveSessionSnapshot(sessionId);
        }

        this.lastSnapshotTime = LocalDateTime.now(); // 更新最后生成时间
    }

    private void saveSessionSnapshot(String sessionId) {
        try {
            Path sessionDir = Paths.get(DEBUG_DIR, sessionId);
            Files.createDirectories(sessionDir);

            ConversationMemory l3 = repository.findFirstBySessionIdAndLevelAndIsActiveTrueOrderByCreatedAtDesc(sessionId, 3);
            writeFile(sessionDir.resolve("03_CORE_MEMORY.txt"), formatContent("核心画像 (L3)", l3));

            List<ConversationMemory> l2List = repository.findBySessionIdAndLevelAndIsActiveTrueOrderByCreatedAtDesc(sessionId, 2)
                    .stream().limit(3).collect(Collectors.toList());
            writeFile(sessionDir.resolve("02_DEEP_LOGIC.txt"), formatListContent("深层逻辑 (L2)", l2List));

            List<ConversationMemory> l1List = repository.findBySessionIdAndLevelAndIsActiveTrueOrderByCreatedAtDesc(sessionId, 1)
                    .stream().limit(5).collect(Collectors.toList());
            writeFile(sessionDir.resolve("01_SHORT_TERM.txt"), formatListContent("近期事实 (L1)", l1List));

        } catch (IOException e) {
            log.error("生成快照失败：{}", sessionId, e);
        }
    }

    private String formatContent(String title, ConversationMemory m) {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(title).append("]\n");
        sb.append("最后更新: ").append(LocalDateTime.now().format(DTF)).append("\n");
        if (m != null) {
            sb.append("来源 IDs: ").append(m.getSourceIds()).append("\n");
            sb.append("--------------------------------------------------\n");
            sb.append(m.getContent());
        } else {
            sb.append("(暂无数据)");
        }
        return sb.toString();
    }

    private String formatListContent(String title, List<ConversationMemory> list) {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(title).append("]\n");
        sb.append("最后更新: ").append(LocalDateTime.now().format(DTF)).append("\n");
        sb.append("记录数: ").append(list.size()).append("\n");
        sb.append("--------------------------------------------------\n");
        for (int i = 0; i < list.size(); i++) {
            ConversationMemory m = list.get(i);
            sb.append("[#").append(m.getId()).append("] ").append(m.getContent()).append("\n\n");
        }
        if (list.isEmpty()) sb.append("(暂无数据)");
        return sb.toString();
    }

    private void writeFile(Path path, String content) throws IOException {
        Path tempPath = Files.createTempFile(path.getParent(), "tmp_", ".txt");
        try (FileWriter writer = new FileWriter(tempPath.toFile())) {
            writer.write(content);
        }
        Files.move(tempPath, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    // 新增 getter
    public LocalDateTime getLastSnapshotTime() {
        return lastSnapshotTime;
    }
}