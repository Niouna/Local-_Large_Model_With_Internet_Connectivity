package cn.edu.wtc.cli;

import cn.edu.wtc.ollama.client.OllamaClient;
import cn.edu.wtc.ollama.config.OllamaConfig;
import cn.edu.wtc.manager.OllamaServiceManager;
import cn.edu.wtc.ollama.service.ChatService;
import cn.edu.wtc.ollama.service.SessionManager;

public class Main {
    public static void main(String[] args) {
        System.out.println("=== Ollama Java客户端 完整演示 ===");

        // 1. 启动双路 Ollama 服务
        System.out.println("🔄 初始化 Ollama 服务...");
        if (!OllamaServiceManager.ensureAllRunning()) {  // 修改这里
            System.err.println("❌ Ollama 服务启动失败，程序退出");
            return;
        }
        System.out.println(OllamaServiceManager.getAllStatus());

        // 2. 初始化核心组件
        OllamaConfig config = new OllamaConfig();
        config.loadFromFile();
        OllamaClient client = new OllamaClient(config);
        SessionManager sessionManager = new SessionManager();
        ChatService chatService = new ChatService(client, sessionManager);

        // 3. 启动交互界面
        InteractiveChat chat = new InteractiveChat(chatService);
        chat.start();
    }
}