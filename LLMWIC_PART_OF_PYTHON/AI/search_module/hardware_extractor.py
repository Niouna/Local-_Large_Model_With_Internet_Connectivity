# hardware_extractor.py
"""
硬件/数码站点专用信息提取
针对 ZOL、太平洋、超能网、Chiphell 等站点的内容优化
"""

import re


def extract_hardware_info(text: str, source: str = "") -> dict:
    """
    提取硬件产品信息（CPU、显卡、主板等）

    Args:
        text: 正文内容
        source: 来源站点URL（用于判断站点类型）

    Returns:
        dict: 提取的产品、价格、规格等
    """
    result = {
        "products": [],
        "prices": [],
        "specs": [],
        "scores": [],  # 跑分/排名/关注度
        "recommendations": []
    }

    # 判断站点类型
    is_zol_ranking = "zol.com.cn" in source and "top.zol.com.cn" in source
    is_pconline = "pconline.com.cn" in source
    is_expreview = "expreview.com" in source
    is_chiphell = "chiphell.com" in source
    is_ithome = "ithome.com" in source

    # ===== 1. CPU型号提取（AMD/Intel）=====
    cpu_patterns = [
        # AMD Ryzen 系列（如 Ryzen 7 9800X3D, Ryzen 5 7600X）
        (r'Ryzen\s+(\d)\s+(\d{4}[A-Z]*(?:X3D|G|GE|X|XT)?)', "AMD Ryzen {} {}"),
        # AMD 锐龙 中文
        (r'锐龙\s+(\d)\s+(\d{4}[A-Z]*)', "Ryzen {} {}"),
        # AMD Threadripper
        (r'Threadripper\s+(\d{4}[A-Z]*)', "Threadripper {}"),
        # AMD EPYC
        (r'EPYC\s+(\d{4}[A-Z]*)', "EPYC {}"),
        # Intel Core 系列（如 Core i9-14900K, Core i7-14700）
        (r'Core\s+i(\d)[-\s](\d{4}[A-Z]*)', "Core i{}-{}"),
        # Intel 酷睿 中文
        (r'酷睿\s+i?(\d)[-\s]?(\d{4}[A-Z]*)', "Core i{}-{}"),
        # Intel Ultra 系列
        (r'Ultra\s+(\d)[-\s](\d{3}[A-Z]*)', "Ultra {}-{}"),
        # 简单型号匹配（兜底，如 9800X3D, 14900K, 7800X3D）
        (r'\b(\d{4}X3D|\d{4}K|\d{4}KF|\d{4}F|\d{4}X)\b', "{}"),
    ]

    products_found = set()
    for pattern, template in cpu_patterns:
        matches = re.findall(pattern, text, re.IGNORECASE)
        for match in matches:
            if isinstance(match, tuple):
                model = template.format(*match)
            else:
                model = template.format(match)

            # 清理
            model = model.strip().upper()
            if len(model) > 3 and len(model) < 30:
                products_found.add(model)

    # 添加常见型号（如果在文本中明确提到）
    common_cpus = [
        "9800X3D", "7800X3D", "7600X3D", "7500F", "7600", "7600X",
        "7700", "7700X", "7900", "7900X", "7950X", "7950X3D",
        "5600", "5600X", "5600G", "5700X", "5700X3D", "5800X", "5800X3D", "5900X",
        "5500", "4500",
        "14900K", "14700K", "14600K", "14500", "14400",
        "13900K", "13700K", "13600K", "13500", "13400",
        "12900K", "12700K", "12600K", "12400",
    ]
    for cpu in common_cpus:
        # 确保是独立单词，不是其他词的一部分
        if re.search(r'\b' + re.escape(cpu) + r'\b', text, re.IGNORECASE):
            products_found.add(cpu)

    result["products"] = sorted(list(products_found))[:10]  # 最多10个

    # ===== 2. 显卡型号提取 =====
    gpu_patterns = [
        # NVIDIA RTX 系列
        (r'RTX\s+(\d{4})\s*(Ti|SUPER)?', "RTX {} {}"),
        # AMD RX 系列
        (r'RX\s+(\d{4})\s*(XT|XTX)?', "RX {} {}"),
        # Intel Arc
        (r'Arc\s+A(\d{3})', "Arc A{}"),
    ]

    gpus_found = set()
    for pattern, template in gpu_patterns:
        matches = re.findall(pattern, text, re.IGNORECASE)
        for match in matches:
            if isinstance(match, tuple):
                model = template.format(*match).strip()
            else:
                model = template.format(match).strip()
            if model and len(model) > 3:
                gpus_found.add(model)

    result["products"].extend(sorted(list(gpus_found))[:5])

    # ===== 3. 价格提取（严格模式）=====
    # 要求：必须有货币标识，且数字合理
    price_patterns = [
        # 标准价格格式
        r'[￥¥]\s*(\d{3,5}(?:,\d{3})*)\s*元?',
        r'(\d{3,5}(?:,\d{3})*)\s*[元￥¥]',
        # 明确的价格描述
        r'价格[：:是为]\s*[￥¥]?\s*(\d{3,5})',
        r'售价[：:是为]\s*[￥¥]?\s*(\d{3,5})',
        r'报价[：:是为]\s*[￥¥]?\s*(\d{3,5})',
        r'到手价[：:是为]\s*[￥¥]?\s*(\d{3,5})',
        r'国补价[：:是为]\s*[￥¥]?\s*(\d{3,5})',
        r'券后价[：:是为]\s*[￥¥]?\s*(\d{3,5})',
        r'活动价[：:是为]\s*[￥¥]?\s*(\d{3,5})',
        # 价格区间（取中间值或最低值）
        r'(\d{3,5})\s*[-~至]\s*\d{3,5}\s*元',
    ]

    prices_found = set()
    for pattern in price_patterns:
        matches = re.findall(pattern, text)
        for match in matches:
            price_str = match[0] if isinstance(match, tuple) else match
            price_str = price_str.replace(',', '')

            try:
                price = int(price_str)
                # CPU/显卡价格通常在400-30000之间
                if 400 <= price <= 30000:
                    prices_found.add(f"{price}元")
            except:
                continue

    # 如果是ZOL排行榜页面，过滤掉可能是排名的数字
    if is_zol_ranking:
        # ZOL排行榜的价格通常是4位数，排名/关注度是3位数
        valid_prices = [p for p in prices_found if int(p.replace('元', '')) >= 1000]
        result["prices"] = sorted(list(valid_prices))[:5]

        # 收集可能的排名分数
        score_pattern = r'\b(\d{2,3})\s*分?\b'
        scores = re.findall(score_pattern, text)
        result["scores"] = [f"{s}分" for s in set(scores) if 50 <= int(s) <= 99][:3]
    else:
        result["prices"] = sorted(list(prices_found))[:5]

    # ===== 4. 规格参数提取 =====
    spec_patterns = [
        # 核心/线程
        (r'(\d+)\s*核(?:心)?\s*(\d+)\s*线程?', "{}核{}线程"),
        (r'(\d+)[-\s]core[/\s](\d+)[-\s]thread', "{}核{}线程"),
        # 频率
        (r'(\d+\.\d+)\s*GHz', "{}GHz"),
        (r'基础频率[：:]\s*(\d+\.\d+)', "{}GHz"),
        (r'加速频率[：:]\s*(\d+\.\d+)', "{}GHz"),
        (r'睿频[：:]\s*(\d+\.\d+)', "{}GHz"),
        # 缓存
        (r'(\d+)\s*MB\s*(?:三级缓存|L3|缓存)', "{}MB L3"),
        (r'缓存[：:]\s*(\d+)\s*MB', "{}MB L3"),
        # TDP功耗
        (r'(\d+)\s*W\s*(?:TDP|功耗)', "{}W TDP"),
        (r'TDP[：:]\s*(\d+)\s*W?', "{}W TDP"),
        (r'功耗[：:]\s*(\d+)\s*W?', "{}W"),
        # 制程
        (r'(\d+)\s*nm\s*工艺?', "{}nm"),
        (r'(\d+)\s*纳米', "{}nm"),
    ]

    specs_found = set()
    for pattern, template in spec_patterns:
        matches = re.findall(pattern, text, re.IGNORECASE)
        for match in matches:
            if isinstance(match, tuple):
                spec = template.format(*match)
            else:
                spec = template.format(match)
            if spec:
                specs_found.add(spec)

    result["specs"] = sorted(list(specs_found))[:8]

    # ===== 5. 推荐/结论句提取 =====
    recommendation_keywords = [
        "值得买", "推荐", "首选", "性价比", "闭眼入", "真香",
        "不建议", "避坑", "慎入", "智商税", "翻车",
        "适合", "不适合", "够用", "过剩"
    ]

    sentences = re.split(r'[。！？.!?]', text)
    for sent in sentences[:15]:  # 看前15句
        sent = sent.strip()
        if 10 < len(sent) < 120:  # 长度适中
            if any(kw in sent for kw in recommendation_keywords):
                result["recommendations"].append(sent)

    result["recommendations"] = result["recommendations"][:3]

    return result


def merge_with_original(original_facts: dict, hw_facts: dict) -> dict:
    """
    合并原始提取结果和硬件专用提取结果
    """
    merged = {}

    for key in ["products", "prices", "specs", "scores", "recommendations"]:
        orig = set(original_facts.get(key, []))
        new = set(hw_facts.get(key, []))
        merged[key] = sorted(list(orig | new))[:10]

    # 兼容旧接口的字段
    merged["dates"] = original_facts.get("dates", [])
    merged["data"] = merged["specs"]  # 兼容

    return merged


def is_likely_price(text: str, number: int) -> bool:
    """
    判断一个数字是否可能是价格
    """
    # 价格范围
    if not (400 <= number <= 30000):
        return False

    # 上下文检查
    price_context = [
        "元", "￥", "¥", "价格", "售价", "报价", "到手", "国补", "券后",
        "便宜", "贵", "值得", "买", "入手"
    ]

    # 非价格上下文
    non_price_context = [
        "分", "排名", "热度", "关注", "指数", "评分", "跑分", "万",
        "年", "月", "日", "人", "次"
    ]

    text_lower = text.lower()
    has_price_hint = any(p in text_lower for p in price_context)
    has_non_price = any(n in text_lower for n in non_price_context)

    return has_price_hint and not has_non_price