# info_extractor.py
"""通用信息提取模块 - 不限领域"""

import re


def extract_info_fragments(text: str) -> list:
    """
    提取信息片段 - 通用版

    支持：电子产品、汽车、游戏设备、家电等
    """
    fragments = []

    # ==================== 1. 产品型号提取 ====================
    product_patterns = [
        # 苹果系列
        r'(iPhone\s*\d+[A-Za-z\s]*)',
        r'(iPad\s*[A-Za-z0-9\s]*)',
        r'(MacBook\s*[A-Za-z\s]*\d*)',
        r'(AirPods\s*[A-Za-z0-9\s]*)',
        r'(Apple\s*Watch\s*[A-Za-z0-9\s]*)',

        # 安卓手机
        r'(Samsung\s*Galaxy\s*[A-Za-z0-9\s]*)',
        r'(Xiaomi\s*\d+[A-Za-z\s]*)',
        r'(Huawei\s*[A-Za-z0-9\s]*)',
        r'(OPPO\s*[A-Za-z0-9\s]*)',
        r'(vivo\s*[A-Za-z0-9\s]*)',
        r'(OnePlus\s*\d+[A-Za-z\s]*)',
        r'(Pixel\s*\d+[A-Za-z\s]*)',
        r'(荣耀\s*[A-Za-z0-9\s]*)',
        r'(Realme\s*[A-Za-z0-9\s]*)',

        # 电脑品牌
        r'(ThinkPad\s*[A-Za-z0-9\s]*)',
        r'(Surface\s*[A-Za-z0-9\s]*)',
        r'(Dell\s*[A-Za-z0-9\s]*)',
        r'(HP\s*[A-Za-z0-9\s]*)',
        r'(Lenovo\s*[A-Za-z0-9\s]*)',
        r'(ASUS\s*[A-Za-z0-9\s]*)',
        r'(ROG\s*[A-Za-z0-9\s]*)',
        r'(Alienware\s*[A-Za-z0-9\s]*)',
        r'(Razer\s*[A-Za-z0-9\s]*)',
        r'(MSI\s*[A-Za-z0-9\s]*)',

        # 游戏设备
        r'(PlayStation\s*\d+[A-Za-z\s]*)',
        r'(Xbox\s*Series\s*[A-Za-z\s]*)',
        r'(Nintendo\s*Switch\s*[A-Za-z\s]*)',
        r'(Steam\s*Deck)',

        # 汽车 - 新能源
        r'(特斯拉\s*Model\s*[A-Za-z0-9\s]*)',
        r'(Tesla\s*Model\s*[A-Za-z0-9\s]*)',
        r'(比亚迪\s*[A-Za-z0-9\s]*)',
        r'(小鹏\s*[A-Za-z0-9\s]*)',
        r'(蔚来\s*[A-Za-z0-9\s]*)',
        r'(理想\s*[A-Za-z0-9\s]*)',
        r'(极氪\s*[A-Za-z0-9\s]*)',
        r'(问界\s*[A-Za-z0-9\s]*)',

        # 汽车 - 传统品牌
        r'(宝马\s*[A-Za-z0-9\s]*)',
        r'(奔驰\s*[A-Za-z0-9\s]*)',
        r'(奥迪\s*[A-Za-z0-9\s]*)',
        r'(丰田\s*[A-Za-z0-9\s]*)',
        r'(本田\s*[A-Za-z0-9\s]*)',
        r'(大众\s*[A-Za-z0-9\s]*)',
        r'(保时捷\s*[A-Za-z0-9\s]*)',

        # 通用兜底：品牌 + 数字型号
        r'([A-Z][a-z]{2,}\s*\d{3,4}[A-Za-z]*)',
    ]

    products_found = set()
    for pattern in product_patterns:
        matches = re.findall(pattern, text, re.IGNORECASE)
        products_found.update(matches)

    # 清理空白字符
    products_found = {p.strip() for p in products_found if len(p.strip()) > 2}

    for p in list(products_found)[:8]:
        fragments.append({"type": "product", "content": p})

    # ==================== 2. 价格提取 ====================
    price_patterns = [
        r'[\￥¥$€£]\s*(\d{1,3}(?:,\d{3})*(?:\.\d{1,2})?)',
        r'(\d{1,3}(?:,\d{3})*(?:\.\d{1,2})?)\s*(?:元 | 块|刀 | 美元 | 人民币 | 美金 | 欧元)',
        r'(\d+(?:\.\d+)?)\s*万\s*(?:元 | 块)?',
        r'(?:售价 | 价格 | 定价 | 报价 | 入手)[\s:：]*(\d{1,3}(?:,\d{3})*)',
    ]

    prices_found = set()
    for pattern in price_patterns:
        matches = re.findall(pattern, text)
        for m in matches:
            clean_price = m.replace(',', '')
            prices_found.add(clean_price)

    for p in list(prices_found)[:5]:
        fragments.append({"type": "price", "content": f"{p}"})

    # ==================== 3. 数字 + 单位提取 ====================
    unit_patterns = [
        # 存储/内存
        r'\d+\s*(?:GB|TB|MB|KB|PB)',
        # 频率/性能
        r'\d+(?:\.\d+)?\s*(?:GHz|MHz|THz|nm)',
        # 屏幕
        r'\d+(?:\.\d+)?\s*(?:英寸 | 寸|K 分辨率 | 像素|MP|万像素 | 亿像素)',
        # 电池
        r'\d+\s*(?:mAh|Ah|W|Wh)',
        # 尺寸/重量
        r'\d+(?:\.\d+)?\s*(?:mm|cm|m|kg|g|mg)',
        # 时间
        r'\d+(?:\.\d+)?\s*(?:小时 | 天 | 周 | 月 | 年 | 分钟|s|h)',
        # 其他
        r'\d+(?:\.\d+)?\s*(?:%|核 | 线程 | 瓦 | 伏 | 安 | 欧|dpi|ppi|fps|ms|km|公里)',
        # 中文单位兜底
        r'\d+(?:\.\d+)?\s*(?:毫安 | 千克 | 公斤 | 克 | 毫升 | 升|核 | 线程)',
    ]

    data_found = set()
    for pattern in unit_patterns:
        matches = re.findall(pattern, text, re.IGNORECASE)
        data_found.update(matches)

    for n in list(data_found)[:10]:
        fragments.append({"type": "data", "content": n.strip()})

    # ==================== 4. 日期提取 ====================
    date_patterns = [
        r'\d{4}年\d{1,2}月\d{1,2}日',
        r'\d{4}-\d{2}-\d{2}',
        r'\d{4}/\d{2}/\d{2}',
        r'\d{4}\.\d{2}\.\d{2}',
        r'\d{1,2}月\d{1,2}日',
        r'\d{4}年\d{1,2}月',
        r'(?:Q[1-4]|第 [一二三四] 季度)\s*\d{4}',
        r'\d{4}年(?:春 | 夏 | 秋 | 冬) 季？',
    ]

    dates_found = set()
    for pattern in date_patterns:
        matches = re.findall(pattern, text)
        dates_found.update(matches)

    for d in list(dates_found)[:5]:
        fragments.append({"type": "date", "content": d})

    # ==================== 5. 推荐/结论句提取 ====================
    conclusion_keywords = [
        # 正面
        '值得', '推荐', '性价比', '适合', '最好', '首选', '不错', '优秀', '出色',
        '真香', '很香', '很值', '划算', '实惠', '好评', '满意', '香',
        '值得购买', '值得入手', '可以考虑', '强烈推荐', '建议购买', '闭眼入',
        # 负面
        '失望', '不建议', '避坑', '踩雷', '翻车', '劝退', '智商税', '坑',
        '不值', '不划算', '贵', '差评', '不建议买', '别买',
        # 中性/其他
        '刚需', '等等党', '早买早享受', '晚买享折扣', '首发', '跳水',
    ]

    sentences = re.split(r'[。！？.!?]', text)
    for sent in sentences[:10]:
        sent = sent.strip()
        if 15 < len(sent) < 150:
            if any(kw in sent for kw in conclusion_keywords):
                fragments.append({"type": "recommendation", "content": sent})

    return fragments


def extract_key_facts(snippet: str, title: str = "") -> dict:
    """提取关键事实（兼容旧接口）"""
    text = f"{title} {snippet}"
    fragments = extract_info_fragments(text)

    return {
        "products": [f["content"] for f in fragments if f["type"] == "product"],
        "prices": [f["content"] for f in fragments if f["type"] == "price"],
        "dates": [f["content"] for f in fragments if f["type"] == "date"],
        "data": [f["content"] for f in fragments if f["type"] == "data"],
        "recommendations": [f["content"] for f in fragments if f["type"] == "recommendation"][:3],
    }