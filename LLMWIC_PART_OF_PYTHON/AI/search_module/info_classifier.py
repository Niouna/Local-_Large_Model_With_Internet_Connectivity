# info_classifier.py
"""信息类型分类模块"""

import re


def classify_info_type(text: str) -> dict:
    """分类信息类型"""
    result = {
        "is_fact": False,
        "is_opinion": False,
        "is_recommendation": False,
        "has_data": False,
        "uncertainty_level": "medium"
    }

    text_lower = text.lower()

    # 事实特征
    if re.search(r'\d{4}年|\d{4}-\d{2}-\d{2}', text):
        result["is_fact"] = True
    if re.search(r'\d+[\d,.]*(?:元 |GB|TB|MHz|GHz|%|块 | 台)', text):
        result["has_data"] = True
        result["is_fact"] = True

    # 观点特征
    opinion_keywords = ['觉得', '认为', '感觉', '个人', '可能', '也许', '大概', '应该']
    if any(kw in text_lower for kw in opinion_keywords):
        result["is_opinion"] = True

    # 建议特征
    recommendation_keywords = ['建议', '推荐', '值得', '适合', '可以', '最好', '不妨']
    if any(kw in text_lower for kw in recommendation_keywords):
        result["is_recommendation"] = True

    # 不确定性评估
    uncertainty_keywords = ['可能', '也许', '大概', '据说', '疑似', '不确定']
    uncertainty_count = sum(1 for kw in uncertainty_keywords if kw in text_lower)
    if uncertainty_count >= 3:
        result["uncertainty_level"] = "high"
    elif uncertainty_count >= 1:
        result["uncertainty_level"] = "medium"
    else:
        result["uncertainty_level"] = "low"

    return result