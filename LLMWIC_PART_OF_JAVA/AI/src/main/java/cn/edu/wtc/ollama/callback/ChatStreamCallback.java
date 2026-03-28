package cn.edu.wtc.ollama.callback;

public interface ChatStreamCallback {
    void onChunk(String chunk);
    void onComplete();
    void onError(Throwable t);
}