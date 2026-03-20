package cn.edu.wtc.util;

public class TokenEstimator {
    public static int estimateTokens(String text) {
        return text == null ? 0 : text.length() / 2;
    }
}