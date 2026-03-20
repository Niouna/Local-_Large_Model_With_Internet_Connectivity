# validator.py
"""搜索结果交叉验证模块"""

from collections import Counter


def cross_validate(results: list) -> dict:
    """
    交叉验证多个搜索结果的一致性

    返回：
    - consistent_facts: 多个来源一致的信息
    - conflicting_facts: 存在冲突的信息
    - confidence: 整体置信度
    """
    consistent_facts = []
    conflicting_facts = []

    # 收集所有关键事实
    all_products = []
    all_prices = []
    all_dates = []

    for r in results:
        key_facts = r.get('key_facts', {})

        # 产品
        products = key_facts.get('products', [])
        all_products.extend(products)

        # 价格
        prices = key_facts.get('prices', [])
        all_prices.extend(prices)

        # 日期（兼容字符串和字典两种格式）
        dates = key_facts.get('dates', [])
        for d in dates:
            if isinstance(d, dict):
                all_dates.append(d.get('raw', ''))
            else:
                all_dates.append(str(d))

    # 统计出现频率
    product_counts = Counter(all_products)
    price_counts = Counter(all_prices)
    date_counts = Counter(all_dates)

    # 高频产品（多个来源提到）
    for item, count in product_counts.items():
        if count >= 2 and item:
            consistent_facts.append(f"多来源提及：{item}")
        elif count == 1 and len(results) >= 2 and item:
            conflicting_facts.append(f"单一来源：{item}")

    # 高频价格
    for item, count in price_counts.items():
        if count >= 2 and item:
            consistent_facts.append(f"多来源确认价格：{item}")
        elif count == 1 and len(results) >= 2 and item:
            conflicting_facts.append(f"价格不一致：{item}")

    # 高频日期
    for item, count in date_counts.items():
        if count >= 2 and item:
            consistent_facts.append(f"多来源确认日期：{item}")

    # 确定置信度
    if len(consistent_facts) >= 3:
        confidence = "high"
    elif len(consistent_facts) >= 1:
        confidence = "medium"
    else:
        confidence = "low"

    return {
        "consistent_facts": consistent_facts[:5],
        "conflicting_facts": conflicting_facts[:5],
        "confidence": confidence,
        "source_count": len(results)
    }