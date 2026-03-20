package cn.edu.wtc.relay.search;

public interface SearchService {
    /**
     * 执行搜索，返回可直接用于 prompt 的文本
     * @param query 查询词
     * @param maxResults 最大结果数
     * @return 格式化后的搜索结果
     */
    String search(String query, int maxResults);
}