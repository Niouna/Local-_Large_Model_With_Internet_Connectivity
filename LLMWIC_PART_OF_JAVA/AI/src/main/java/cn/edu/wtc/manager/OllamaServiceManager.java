package cn.edu.wtc.manager;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Ollama 双实例后台服务管理器
 * 实例 1: GPU (A730M) - Port 11434
 * 实例 2: CPU (Force)  - Port 11435
 */
public class OllamaServiceManager {

    // ================= 配置区域 =================

    // 公共路径
    public static final String OLLAMA_PATH = "D:\\Ollama\\ollama-intel-2.3.0b20250923-win";
    private static final String OLLAMA_EXE = "ollama.exe";
    private static final File LOG_DIR = new File("logs");

    // --- 实例 1: GPU (DeepSeek) 配置 ---
    private static final InstanceConfig GPU_CONFIG = new InstanceConfig(
            "GPU-DeepSeek",
            11434,
            Map.of(
                    "ONEAPI_DEVICE_SELECTOR", "level_zero:0", // 只选独显
                    "ZES_ENABLE_SYSMAN", "1",
                    "SYCL_CACHE_PERSISTENT", "1",
                    "SYCL_DEVICE_CHECK", "0",                // 绕过报错
                    "OLLAMA_KEEP_ALIVE", "10m",
                    "OLLAMA_NUM_PARALLEL", "1"
                    // 注意：这里不设置 OLLAMA_NUM_GPU，默认使用 GPU
            )
    );

    // --- 实例 2: CPU (Qwen) 配置 ---
    private static final InstanceConfig CPU_CONFIG = new InstanceConfig(
            "CPU-Qwen",
            11435,
            Map.of(
                    "ONEAPI_DEVICE_SELECTOR", "level_zero:0", // 关键：只看到独显，绕过 "different devices" 报错
                    "SYCL_DEVICE_CHECK", "0",                // 关键：关闭检查
                    "OLLAMA_NUM_GPU", "0",                   // 关键：强制不使用 GPU 计算 (权重在 CPU)
                    "OLLAMA_KEEP_ALIVE", "10m",
                    "OLLAMA_NUM_PARALLEL", "1"
            )
    );

    // ================= 运行时状态 =================
    private static final Map<String, Process> runningProcesses = new HashMap<>();
    private static final Map<String, Boolean> startedByUs = new HashMap<>();

    // ================= 内部类：配置封装 =================
    private static class InstanceConfig {
        final String name;
        final int port;
        final Map<String, String> envVars;
        final File logFile;

        public InstanceConfig(String name, int port, Map<String, String> envVars) {
            this.name = name;
            this.port = port;
            this.envVars = envVars;
            if (!LOG_DIR.exists()) LOG_DIR.mkdirs();
            this.logFile = new File(LOG_DIR, "ollama_" + name.toLowerCase() + ".log");
        }

        public String getHost() {
            return "127.0.0.1:" + port;
        }
    }

    /* -------------------- 对外唯一入口：确保双路运行 -------------------- */
    public static boolean ensureAllRunning() {
        boolean gpuOk = ensureInstanceRunning(GPU_CONFIG);
        boolean cpuOk = ensureInstanceRunning(CPU_CONFIG);
        return gpuOk && cpuOk;
    }

    /* -------------------- 单实例启动逻辑 -------------------- */
    private static boolean ensureInstanceRunning(InstanceConfig config) {
        if (isServiceRunning(config.port)) {
            System.out.println("✅ [" + config.name + "] 已在端口 " + config.port + " 运行");
            return true;
        }
        System.out.print("🚀 正在启动 [" + config.name + "] ...");
        return startInstance(config);
    }

    private static boolean startInstance(InstanceConfig config) {
        try {
            // 构建命令: cmd /c start "Title" cmd /k "cd path && ollama serve"
            // 使用 start 是为了让每个服务在独立的窗口中运行，方便查看实时日志，也避免主 Java 进程阻塞
            List<String> command = new ArrayList<>();
            command.add("cmd.exe");
            command.add("/c");
            command.add("start");
            command.add("\"Ollama-" + config.name + "\"");
            command.add("cmd");
            command.add("/k");

            String cmdStr = String.format("cd /d \"%s\" && echo [ %s ] Starting on port %d... && %s serve",
                    OLLAMA_PATH, config.name, config.port, OLLAMA_EXE);
            command.add(cmdStr);

            ProcessBuilder pb = new ProcessBuilder(command);
            Map<String, String> env = pb.environment();

            // 注入特定环境变量
            env.put("OLLAMA_HOST", "127.0.0.1:" + config.port);
            for (Map.Entry<String, String> entry : config.envVars.entrySet()) {
                env.put(entry.getKey(), entry.getValue());
            }

            // 日志重定向
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(config.logFile));

            Process p = pb.start();
            runningProcesses.put(config.name, p);
            startedByUs.put(config.name, true);

            // 等待启动 (最多 30 秒)
            boolean success = false;
            for (int i = 0; i < 30; i++) {
                Thread.sleep(1000);
                System.out.print(".");
                if (isServiceRunning(config.port)) {
                    success = true;
                    break;
                }
            }

            if (success) {
                System.out.println("\n✅ [" + config.name + "] 启动成功 (Port: " + config.port + ")");
                // 注册关闭钩子 (仅当 JVM 退出时)
                Runtime.getRuntime().addShutdownHook(new Thread(() -> stopInstance(config)));
                return true;
            } else {
                System.err.println("\n❌ [" + config.name + "] 启动超时");
                return false;
            }

        } catch (IOException | InterruptedException e) {
            System.err.println("\n❌ [" + config.name + "] 启动失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /* -------------------- 停止服务 -------------------- */
    public static void stopAllServices() {
        stopInstance(GPU_CONFIG);
        stopInstance(CPU_CONFIG);
    }

    private static void stopInstance(InstanceConfig config) {
        if (!startedByUs.getOrDefault(config.name, false)) return;

        System.out.println("🛑 正在停止 [" + config.name + "] ...");
        try {
            // 尝试调用 API 优雅退出
            sendQuitCommand(config.port);
            Thread.sleep(2000);

            Process p = runningProcesses.get(config.name);
            if (p != null && p.isAlive()) {
                p.destroy();
                if (!p.waitFor(5, TimeUnit.SECONDS)) {
                    p.destroyForcibly();
                }
            }
            System.out.println("✅ [" + config.name + "] 已停止");
            runningProcesses.remove(config.name);
            startedByUs.put(config.name, false);
        } catch (Exception e) {
            System.err.println("⚠️ [" + config.name + "] 停止时出错: " + e.getMessage());
        }
    }

    /* -------------------- 状态检测 -------------------- */
    public static boolean isServiceRunning(int port) {
        try (Socket ignored = new Socket("127.0.0.1", port)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static String getAllStatus() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Ollama 双路服务状态 ===\n");
        sb.append(GPU_CONFIG.name).append(": ").append(isServiceRunning(GPU_CONFIG.port) ? "✅ 运行中 (:11434)" : "❌ 未运行").append("\n");
        sb.append(CPU_CONFIG.name).append(": ").append(isServiceRunning(CPU_CONFIG.port) ? "✅ 运行中 (:11435)" : "❌ 未运行").append("\n");
        return sb.toString();
    }

    /* -------------------- 辅助方法 -------------------- */
    private static void sendQuitCommand(int port) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL("http://127.0.0.1:" + port + "/api/quit").openConnection();
            c.setRequestMethod("GET");
            c.connect();
            c.getInputStream().close();
        } catch (IOException ignored) {}
    }

    public static void viewLog(String instanceName) {
        InstanceConfig config = instanceName.equalsIgnoreCase("gpu") ? GPU_CONFIG :
                instanceName.equalsIgnoreCase("cpu") ? CPU_CONFIG : null;

        if (config == null) {
            System.out.println("未知实例，请输入 'gpu' 或 'cpu'");
            return;
        }

        System.out.println("--- 日志: " + config.logFile.getAbsolutePath() + " ---");
        try (BufferedReader r = new BufferedReader(new FileReader(config.logFile))) {
            // 只显示最后 20 行
            List<String> lines = r.lines().toList();
            int start = Math.max(0, lines.size() - 20);
            for (int i = start; i < lines.size(); i++) {
                System.out.println(lines.get(i));
            }
            if (lines.isEmpty()) System.out.println("(日志为空)");
        } catch (IOException e) {
            System.out.println("无法读取日志: " + e.getMessage());
        }
    }

    public static String getModels(int port) {
        if (!isServiceRunning(port)) return "服务未运行";
        try {
            HttpURLConnection c = (HttpURLConnection) new URL("http://127.0.0.1:" + port + "/api/tags").openConnection();
            c.setRequestMethod("GET");
            try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()))) {
                return r.lines().reduce("", (a, b) -> a + b);
            }
        } catch (IOException e) {
            return "获取失败: " + e.getMessage();
        }
    }

    /* -------------------- 测试入口 (CLI) -------------------- */
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        while (true) {
            System.out.println("\n==============================");
            System.out.println(getAllStatus());
            System.out.println("==============================");
            System.out.println("1. 启动双路服务 (Start All)");
            System.out.println("2. 停止双路服务 (Stop All)");
            System.out.println("3. 仅启动/检查 GPU (11434)");
            System.out.println("4. 仅启动/检查 CPU (11435)");
            System.out.println("5. 查看 GPU 日志");
            System.out.println("6. 查看 CPU 日志");
            System.out.println("7. 列出 GPU 模型");
            System.out.println("8. 列出 CPU 模型");
            System.out.println("0. 退出");
            System.out.print("请选择: ");

            String choice = sc.nextLine().trim();
            switch (choice) {
                case "1": ensureAllRunning(); break;
                case "2": stopAllServices(); break;
                case "3": ensureInstanceRunning(GPU_CONFIG); break;
                case "4": ensureInstanceRunning(CPU_CONFIG); break;
                case "5": viewLog("gpu"); break;
                case "6": viewLog("cpu"); break;
                case "7": System.out.println(getModels(GPU_CONFIG.port)); break;
                case "8": System.out.println(getModels(CPU_CONFIG.port)); break;
                case "0":
                    stopAllServices();
                    System.out.println("再见！");
                    return;
                default: System.out.println("无效选择");
            }
        }
    }
}