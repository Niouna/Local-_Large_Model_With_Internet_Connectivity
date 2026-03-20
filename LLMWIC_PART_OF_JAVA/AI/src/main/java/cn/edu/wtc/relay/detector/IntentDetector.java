package cn.edu.wtc.relay.detector;

import org.springframework.stereotype.Component;
import java.util.Arrays;
import java.util.List;

@Component
public class IntentDetector {
    private static final List<String> SEARCH_TRIGGERS = Arrays.asList(
            "请联网回答", "联网搜索", "搜索一下", "查一查最新的",
            "online search", "search the web"
    );

    public boolean needsWebSearch(String message) {
        if (message == null || message.isBlank()) return false;
        String lower = message.toLowerCase();
        return SEARCH_TRIGGERS.stream().anyMatch(trigger -> lower.contains(trigger.toLowerCase()));
    }

    public String extractQuery(String message) {
        if (message == null) return "";
        String cleaned = message;
        for (String trigger : SEARCH_TRIGGERS) {
            cleaned = cleaned.replace(trigger, "");
        }
        return cleaned.trim();
    }
}