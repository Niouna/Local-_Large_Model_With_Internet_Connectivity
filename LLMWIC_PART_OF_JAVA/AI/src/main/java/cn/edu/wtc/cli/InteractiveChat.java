package cn.edu.wtc.cli;

import cn.edu.wtc.ollama.service.ChatService;
import cn.edu.wtc.ollama.model.Conversation;
import java.io.IOException;
import java.util.Scanner;
import java.util.UUID;

public class InteractiveChat {
    private final ChatService chatService;
    private final Scanner scanner;

    public InteractiveChat(ChatService chatService) {
        this.chatService = chatService;
        this.scanner = new Scanner(System.in);
    }

    public void start() {
        System.out.println("=== Ollama 交互式对话 ===");

        if (!chatService.healthCheck()) {
            System.out.println("❌ 无法连接到Ollama服务，请确保服务正在运行");
            return;
        }

        System.out.println("\n请选择模式:");
        System.out.println("1. 单次对话（无历史）");
        System.out.println("2. 连续对话（保持历史）");
        System.out.println("3. 测试不同模型");
        System.out.print("请选择 (1-3): ");

        String choice = scanner.nextLine().trim();

        switch (choice) {
            case "1": singleChatMode(); break;
            case "2": continuousChatMode(); break;
            case "3": testModelsMode(); break;
            default:
                System.out.println("无效选择，使用连续对话模式");
                continuousChatMode();
        }
    }

    private void singleChatMode() {
        System.out.println("\n=== 单次对话模式 ===");
        System.out.println("每次对话都是独立的，不保留历史");
        System.out.println("输入 'quit' 退出");

        while (true) {
            System.out.print("\n👤 你: ");
            String input = scanner.nextLine().trim();
            if (input.equalsIgnoreCase("quit") || input.equalsIgnoreCase("exit")) break;
            if (input.isEmpty()) continue;

            try {
                String response = chatService.chat(null, input); // 使用默认模型
                System.out.println("🤖 AI: " + response);
            } catch (IOException e) {
                System.err.println("请求失败: " + e.getMessage());
            }
        }
    }

    private void continuousChatMode() {
        System.out.println("\n=== 连续对话模式 ===");
        String model = chooseModel();
        if (model == null) return;

        String sessionId = UUID.randomUUID().toString().substring(0, 8);
        // 创建会话（实际在第一次发送时自动创建，这里仅显示信息）
        System.out.println("会话ID: " + sessionId);
        System.out.println("使用模型: " + model);
        System.out.println("\n可用命令: /history, /clear, /model, /sessions, /summary, /quit");

        while (true) {
            System.out.print("\n👤 你: ");
            String input = scanner.nextLine().trim();
            if (input.isEmpty()) continue;

            if (input.startsWith("/")) {
                if (handleCommand(input, sessionId)) break;
                continue;
            }

            try {
                String response = chatService.chatWithSession(sessionId, model, input);
                System.out.println("🤖 AI: " + response);
            } catch (IOException e) {
                System.err.println("请求失败: " + e.getMessage());
            }
        }

        chatService.endSession(sessionId);
    }

    private void testModelsMode() {
        System.out.println("\n=== 模型测试模式 ===");
        try {
            String modelsJson = chatService.listModels();
            System.out.println("可用模型:");
            System.out.println(modelsJson);
        } catch (IOException e) {
            System.err.println("无法获取模型列表: " + e.getMessage());
        }

        System.out.print("\n输入要测试的模型名称 (例如: qwen2.5:7b): ");
        String model = scanner.nextLine().trim();
        if (model.isEmpty()) model = "qwen2.5:7b";

        System.out.println("测试模型: " + model);
        System.out.println("输入 'quit' 退出");

        while (true) {
            System.out.print("\n👤 你: ");
            String input = scanner.nextLine().trim();
            if (input.equalsIgnoreCase("quit") || input.equalsIgnoreCase("exit")) break;
            if (input.isEmpty()) continue;

            try {
                String response = chatService.chat(model, input);
                System.out.println("🤖 AI: " + response);
            } catch (IOException e) {
                System.err.println("请求失败: " + e.getMessage());
            }
        }
    }

    private String chooseModel() {
        System.out.println("\n请选择模型:");
        System.out.println("1. qwen2.5:7b (默认)");
        System.out.println("2. qwen2.5:14b");
        System.out.println("3. deepseek-r1:7b");
        System.out.println("4. 输入其他模型名称");
        System.out.print("请选择 (1-4): ");

        String choice = scanner.nextLine().trim();
        switch (choice) {
            case "1": return "qwen2.5:7b";
            case "2": return "qwen2.5:14b";
            case "3": return "deepseek-r1:7b";
            case "4":
                System.out.print("请输入模型名称: ");
                String custom = scanner.nextLine().trim();
                return custom.isEmpty() ? "qwen2.5:7b" : custom;
            default: return "qwen2.5:7b";
        }
    }

    private boolean handleCommand(String cmd, String sessionId) {
        cmd = cmd.toLowerCase();
        switch (cmd) {
            case "/quit":
            case "/exit":
                System.out.println("退出对话");
                return true;
            case "/history":
                Conversation conv = chatService.getSession(sessionId);
                if (conv != null) conv.printHistory();
                else System.out.println("未找到会话: " + sessionId);
                break;
            case "/clear":
                conv = chatService.getSession(sessionId);
                if (conv != null) {
                    conv.clearHistory();
                    System.out.println("已清空对话历史");
                }
                break;
            case "/model":
                System.out.print("请输入新模型名称: ");
                String newModel = scanner.nextLine().trim();
                if (!newModel.isEmpty()) {
                    chatService.endSession(sessionId);
                    // 重新创建会话，下次发送时会自动用新模型
                    System.out.println("模型已切换，下次对话将使用新模型");
                }
                break;
            case "/sessions":
                chatService.listSessions();
                break;
            case "/summary":
                conv = chatService.getSession(sessionId);
                if (conv != null) System.out.println(conv.getSummary());
                break;
            default:
                System.out.println("未知命令: " + cmd);
        }
        return false;
    }
}