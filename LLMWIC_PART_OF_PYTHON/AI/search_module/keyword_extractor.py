# search_module/keyword_extractor.py
"""
关键词提取模块
基于 jieba 分词和词性过滤，从自然语言中提取核心搜索关键词
"""

import jieba
import jieba.posseg as pseg
import re

# 允许保留的词性（可根据需要调整）
ALLOWED_FLAGS = {'n', 'v', 'a', 't', 'nr', 'ns', 'nt', 'nz'}  # 名词、动词、形容词、时间词、专名等

# 停用词（可扩展）
STOP_WORDS = {'的', '了', '是', '在', '和', '与', '或', '有', '我', '你', '他', '她', '它',
              '这', '那', '哪', '什么', '怎么', '如何', '为什么', '哪个', '哪些', '谁'}

# 加载自定义词典（可选，可在同目录放置 user_dict.txt）
try:
    jieba.load_userdict(r"D:\个人项目\Local _Large_Model_With_Internet_Connectivity\LLMWIC_PART_OF_PYTHON\AI\user_dict\user_dict.txt")
except:
    pass


def extract_keywords(text: str, max_words: int = 5) -> str:
    """
    从文本中提取核心关键词，用空格分隔

    Args:
        text: 原始用户输入（已去除触发词）
        max_words: 最多保留的关键词数量

    Returns:
        空格分隔的关键词字符串，若结果为空则返回原文本
    """
    # 分词并词性标注
    words = pseg.cut(text)

    keywords = []
    for word, flag in words:
        # 过滤停用词和单字
        if len(word) > 1 and word not in STOP_WORDS:
            # 保留允许的词性
            if flag in ALLOWED_FLAGS:
                keywords.append(word)

    # 去重（保持顺序）
    seen = set()
    unique = []
    for w in keywords:
        if w not in seen:
            seen.add(w)
            unique.append(w)

    # 限制数量
    if len(unique) > max_words:
        # 简单取前 max_words 个（可根据 TF-IDF 等优化，但此处足够）
        unique = unique[:max_words]

    result = " ".join(unique)
    if not result.strip():
        # 如果提取结果为空，回退到原文本（但去除可能残留的单个汉字）
        cleaned = re.sub(r'[^\w\s]', '', text)
        cleaned = ' '.join([w for w in cleaned.split() if len(w) > 1])
        return cleaned if cleaned else text
    return result


# 简单测试
if __name__ == '__main__':
    tests = [
        "帮我推荐几个现在性价比最高的汽车",
        "2026年性价比最高的显卡",
        "北京明天天气怎么样",
        "崩铁目前较为无脑的配队",
        "我想买一部拍照好的手机，价格3000左右",
    ]
    for t in tests:
        print(f"原句: {t}")
        print(f"关键词: {extract_keywords(t)}")
        print("-" * 30)