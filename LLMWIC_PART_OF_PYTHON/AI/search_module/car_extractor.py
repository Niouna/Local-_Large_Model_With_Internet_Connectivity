# car_extractor.py
"""
汽车/新能源车型专用信息提取
针对汽车之家、懂车帝、网易汽车、腾讯汽车等站点优化
"""

import re


def extract_car_info(text: str, source: str = "") -> dict:
    """
    提取汽车型号、价格、续航等关键信息

    Args:
        text: 正文内容
        source: 来源URL（用于判断站点类型）

    Returns:
        dict: 车型、价格、规格、品牌等
    """
    result = {
        "products": [],  # 具体车型
        "prices": [],  # 价格信息
        "specs": [],  # 续航/电池/充电/性能
        "brands": [],  # 品牌
        "scores": [],  # 评分/排名
    }

    # 判断来源类型
    is_dongchedi = "dongchedi.com" in source
    is_autohome = "autohome.com.cn" in source
    is_163_car = "163.com" in source and ("auto" in source or "dy/article" in source)

    # ===== 1. 新能源品牌及车型识别 =====

    # 品牌列表（中文+英文）
    brand_mapping = {
        # 自主品牌
        "比亚迪": ["秦", "汉", "唐", "宋", "元", "海豚", "海豹", "海鸥", "驱逐舰", "护卫舰", "e2", "e3"],
        "特斯拉": ["Model 3", "Model Y", "Model S", "Model X", "Cybertruck"],
        "蔚来": ["ET5", "ET5T", "ET7", "ES6", "ES7", "ES8", "EC6", "EC7", "EP9"],
        "小鹏": ["P5", "P7", "P7i", "G3", "G3i", "G6", "G9", "X9", "MONA M03"],
        "理想": ["L6", "L7", "L8", "L9", "MEGA", "ONE"],
        "极氪": ["001", "007", "009", "X", "FR"],
        "问界": ["M5", "M7", "M9"],
        "小米": ["SU7", "SU7 Pro", "SU7 Max"],
        "埃安": ["S", "S Plus", "Y", "Y Plus", "V", "V Plus", "LX", "Hyper GT", "Hyper SSR"],
        "深蓝": ["SL03", "S7", "S05", "G318"],
        "零跑": ["C01", "C10", "C11", "C16", "T03"],
        "哪吒": ["S", "GT", "X", "AYA", "V"],
        "岚图": ["FREE", "梦想家", "追光"],
        "智己": ["L6", "L7", "LS6", "LS7"],
        "极狐": ["阿尔法S", "阿尔法T", "考拉"],
        "阿维塔": ["11", "12", "15"],
        "智界": ["S7", "R7"],
        "享界": ["S9"],

        # 合资品牌
        "大众": ["ID.3", "ID.4", "ID.4 X", "ID.4 CROZZ", "ID.6", "ID.6 X", "ID.6 CROZZ", "ID.7", "ID.Buzz"],
        "宝马": ["i3", "i4", "i5", "i7", "iX", "iX1", "iX3"],
        "奔驰": ["EQA", "EQB", "EQC", "EQE", "EQE SUV", "EQS", "EQS SUV"],
        "奥迪": ["Q2L e-tron", "Q4 e-tron", "Q5 e-tron", "e-tron", "e-tron GT"],
        "丰田": ["bZ3", "bZ4X", "bZ3X"],
        "本田": ["e:NS1", "e:NP1", "猎光", "烨"],
        "日产": ["ARIYA", "艾睿雅", "N7"],
        "别克": ["E4", "E5", "微蓝6", "微蓝7"],
        "凯迪拉克": ["LYRIQ", "锐歌", "OPTIQ", "傲歌"],

        # 传统品牌新能源
        "荣威": ["D6", "D7", "D5X", "iMAX8 EV", "Ei5", "科莱威"],
        "名爵": ["MG4", "MG4 EV", "Cyberster", "ZS EV"],
        "吉利": ["银河E5", "银河E8", "银河L6", "银河L7", "几何A", "几何C", "几何E", "帝豪EV", "熊猫mini"],
        "长安": ["深蓝SL03", "深蓝S7", "启源A05", "启源A07", "启源Q05", "Lumin", "UNI-K iDD", "UNI-V iDD"],
        "长城": ["欧拉好猫", "欧拉芭蕾猫", "欧拉闪电猫", "坦克300 Hi4-T", "坦克400 Hi4-T", "坦克500 Hi4-T", "蓝山",
                 "高山"],
        "奇瑞": ["eQ1", "小蚂蚁", "QQ冰淇淋", "无界Pro", "舒享家", "iCAR 03", "风云A8", "风云T9"],
        "广汽": ["AION S", "AION Y", "AION V", "AION LX", "昊铂GT", "昊铂HT", "昊铂SSR"],
        "北汽": ["EU5", "EU7", "EX3", "EX5", "魔方", "考拉"],
        "江淮": ["钇为3", "花仙子", "iEV6E", "iC5"],
        "海马": ["爱尚EV", "7X-E"],
    }

    # 英文品牌名映射
    en_brands = {
        "tesla": "特斯拉",
        "byd": "比亚迪",
        "nio": "蔚来",
        "xpeng": "小鹏",
        "li auto": "理想",
        "zeekr": "极氪",
        "aito": "问界",
        "xiaomi": "小米",
        "gac": "埃安",
        "deepal": "深蓝",
        "leapmotor": "零跑",
        "neta": "哪吒",
        "voyah": "岚图",
        "im": "智己",
        "arcfox": "极狐",
        "avatr": "阿维塔",
        "luxeed": "智界",
        "stelato": "享界",
        "bmw": "宝马",
        "mercedes": "奔驰",
        "audi": "奥迪",
        "volkswagen": "大众",
        "vw": "大众",
        "toyota": "丰田",
        "honda": "本田",
        "nissan": "日产",
        "buick": "别克",
        "cadillac": "凯迪拉克",
    }

    products_found = set()
    brands_found = set()

    # 方法1：品牌+车型匹配
    for brand, models in brand_mapping.items():
        # 匹配品牌
        brand_pattern = r'\b' + re.escape(brand) + r'\b'
        if re.search(brand_pattern, text):
            brands_found.add(brand)

            # 匹配该品牌下的车型
            for model in models:
                # 车型格式：品牌+车型 或 单独车型（上下文有品牌）
                model_patterns = [
                    re.escape(brand) + r'[\s\.]*' + re.escape(model),
                    re.escape(model) + r'(?:\s*Pro|\s*Max|\s*Plus|\s*版|\s*EV|\s*PHEV|\s*DM-i|\s*DM-p)?',
                ]

                for pattern in model_patterns:
                    if re.search(pattern, text, re.IGNORECASE):
                        full_name = f"{brand} {model}".strip()
                        products_found.add(full_name)

    # 方法2：英文品牌名
    for en_brand, cn_brand in en_brands.items():
        if re.search(r'\b' + en_brand + r'\b', text, re.IGNORECASE):
            brands_found.add(cn_brand)

    # 方法3：常见车型格式兜底
    car_patterns = [
        # 特斯拉
        r'(Model\s*[3YSX](?:\s*Pro|Max|Plus|高性能|长续航|标准续航)?)',
        # 比亚迪系列
        r'(秦\s*(?:L|Plus|Pro|EV|DM-i|DM-p)?\s*\d*)',
        r'(汉\s*(?:EV|DM-i|DM-p|DM|创世版|千山翠)?)',
        r'(唐\s*(?:EV|DM-i|DM-p|DM)?)',
        r'(宋\s*(?:Pro|Plus|Max|EV|DM-i|DM-p)?)',
        r'(元\s*(?:Plus|Pro|UP)?)',
        r'(海豚|海豹|海鸥|海狮|驱逐舰\d+|护卫舰\d+)'
        # 理想
        r'(理想\s*[LMS]\d{1,2}(?:\s*Pro|Max|Ultra|Air)?)',
        # 小鹏
        r'(小鹏\s*[PGMX]\d{1,2}(?:\s*Pro|Max|Plus)?)',
        # 蔚来
        r'(ET\d(?:T)?|ES\d|EC\d|EP\d)',
        # 小米
        r'(SU7(?:\s*Pro|Max)?)',
        # 通用：数字+字母组合（如001、007）
        r'\b(00[1-9]|0\d{2})(?:\s*FR|Ultra)?',
    ]

    for pattern in car_patterns:
        matches = re.findall(pattern, text, re.IGNORECASE)
        for match in matches:
            match_str = match.strip() if isinstance(match, str) else ' '.join(match).strip()
            if match_str and len(match_str) > 1:
                products_found.add(match_str)

    # 方法4：从上下文推断品牌
    # 如果文本里有"比亚迪"和"汉"，但前面没组合，补全
    text_for_context = text
    for brand in brands_found:
        if brand in ["比亚迪", "特斯拉", "蔚来", "小鹏", "理想", "极氪", "问界", "小米"]:
            # 找紧跟的数字/字母组合
            pattern = re.escape(brand) + r'[的\s]*(\d{1,2}[A-Z]*|[A-Z]\d{1,2})'
            matches = re.findall(pattern, text_for_context)
            for m in matches:
                products_found.add(f"{brand} {m}")

    result["brands"] = sorted(list(brands_found))
    result["products"] = sorted(list(products_found))[:15]  # 最多15个

    # ===== 2. 汽车价格提取（复杂格式） =====

    # 汽车价格特点：10.98万、15.99万元、109800元、补贴后11.98万、限时优惠12.98万
    price_patterns = [
        # xx.xx万（最常见）
        r'(\d{2,3}\.\d{1,2})\s*[万w]\s*(?:元|起|左右|上下|以内)?',
        # xx万（整数）
        r'(\d{2,3})\s*[万w]\s*(?:元|起|左右|上下|以内)?',
        # xxxxx元（完整数字）
        r'(\d{5,6})\s*元',
        # 价格/售价/补贴后/到手价 + 数字
        r'(?:售价|价格|指导价|裸车价|落地价|补贴后|优惠后|到手价|限时价|活动价)[^\d]*(\d{2,3}\.\d{1,2})\s*万',
        r'(?:售价|价格|指导价|裸车价|落地价|补贴后|优惠后|到手价|限时价|活动价)[^\d]*(\d{5,6})\s*元',
        # 降幅/降价/直降 + 数字（辅助判断）
        r'(?:降|跌|优惠|便宜|省)[^\d]*(\d{1,2}\.\d{1,2})\s*万',
        r'(?:降|跌|优惠|便宜|省)[^\d]*(\d{2,3})\s*万',
        # 新增宽松匹配
        r'(?:来到|低至|仅需|只要|才|仅)[^\d]*(\d{2,3}\.\d{1,2})\s*万',
        r'(?:来到|低至|仅需|只要|才|仅)[^\d]*(\d{2,3})\s*万',
        r'价格[^\d]*(\d{2,3}\.\d{1,2})\s*万',
        r'售价[^\d]*(\d{2,3}\.\d{1,2})\s*万',
    ]

    prices_found = set()
    for pattern in price_patterns:
        matches = re.findall(pattern, text, re.IGNORECASE)
        for match in matches:
            try:
                if isinstance(match, tuple):
                    match = match[0]

                match_str = str(match).strip()

                # 统一格式
                if '.' in match_str:
                    # xx.xx万
                    price_val = float(match_str)
                    if 3.0 <= price_val <= 100.0:  # 汽车价格区间：3万-100万
                        prices_found.add(f"{price_val}万元")
                else:
                    # 可能是xx万（整数）或xxxxx元
                    num = int(match_str)
                    if 30000 <= num <= 1000000:  # 元为单位
                        prices_found.add(f"{num}元")
                    elif 3 <= num <= 100:  # 万为单位（省略小数点）
                        prices_found.add(f"{num}万元")
            except:
                continue

    # 排序：按数值从小到大
    def price_sort_key(p):
        nums = re.findall(r'\d+\.?\d*', p)
        if nums:
            val = float(nums[0])
            if '万' in p:
                return val * 10000  # 统一转成元比较
            return val
        return 0

    result["prices"] = sorted(list(prices_found), key=price_sort_key)[:6]

    # ===== 3. 关键参数提取 =====

    spec_patterns = [
        # 续航（最重要）
        (r'(\d{3,3})\s*(?:km|公里|千米)(?:续航|综合续航|纯电续航|最大续航|CLTC|NEDC|WLTP)?', "{}km续航"),
        (r'续航[^\d]*(\d{3,4})\s*(?:km|公里|千米|KM)', "{}km续航"),
        (r'CLTC[^\d]*(\d{3,4})', "CLTC {}km"),
        (r'NEDC[^\d]*(\d{3,4})', "NEDC {}km"),
        (r'WLTP[^\d]*(\d{3,4})', "WLTP {}km"),
        (r'综合续航[^\d]*(\d{3,4})', "综合续航{}km"),
        (r'纯电续航[^\d]*(\d{3,4})', "纯电续航{}km"),

        # 电池容量
        (r'(\d{2,3})\s*(?:kWh|度|千瓦时)(?:电池|电池组|电池包|容量)?', "{}kWh电池"),
        (r'电池[^\d]*(\d{2,3})\s*(?:kWh|度|千瓦时)', "{}kWh电池"),
        (r'搭载[^\d]*(\d{2,3})\s*(?:kWh|度)', "{}kWh"),

        # 充电时间
        (r'(\d{1,2}\.?\d*)\s*小时(?:快充|慢充|充电|充满|充满电)', "{}h充电"),
        (r'快充[^\d]*(\d{1,2}\.?\d*)\s*(?:min|分钟|分)', "{}分钟快充"),
        (r'(\d{1,2})\s*分钟(?:快充|充电|充至|从\d+%充至\d+%)', "{}分钟快充"),
        (r'(\d{1,2})\s*秒(?:破百|加速|百公里)', "{}s破百"),
        (r'零百[^\d]*(\d{1,2}\.?\d*)', "{}s零百加速"),
        (r'百公里加速[^\d]*(\d{1,2}\.?\d*)', "{}s百公里加速"),

        # 动力参数
        (r'(\d{2,3})\s*kW(?:功率|电机功率|最大功率)', "{}kW功率"),
        (r'(\d{3,4})\s*N·m(?:扭矩|电机扭矩|峰值扭矩)', "{}N·m扭矩"),
        (r'(\d{2,3})\s*Ps(?:马力|匹)', "{}Ps马力"),

        # 尺寸（辅助）
        (r'轴距[^\d]*(\d{4})\s*mm', "轴距{}mm"),
        (r'(\d{4})\s*mm(?:轴距)', "轴距{}mm"),
    ]

    specs_found = set()
    for pattern, template in spec_patterns:
        matches = re.findall(pattern, text, re.IGNORECASE)
        for match in matches:
            spec = template.format(match)
            specs_found.add(spec)

    # 去重并排序（续航优先）
    def spec_priority(s):
        if '续航' in s:
            return (0, s)
        elif 'kWh' in s:
            return (1, s)
        elif '分钟' in s or 'h' in s:
            return (2, s)
        elif 's' in s:
            return (3, s)
        else:
            return (4, s)

    result["specs"] = sorted(list(specs_found), key=spec_priority)[:10]

    # ===== 4. 评分/口碑提取 =====

    score_patterns = [
        r'(\d\.\d)\s*分(?:评分|口碑|评价)',
        r'评分[^\d]*(\d\.\d)',
        r'口碑[^\d]*(\d\.\d)',
        r'(\d\.\d)\s*星(?:好评|评价)',
    ]

    scores_found = set()
    for pattern in score_patterns:
        matches = re.findall(pattern, text)
        for match in matches:
            try:
                score = float(match)
                if 3.0 <= score <= 5.0:
                    scores_found.add(f"{score}分")
            except:
                continue

    result["scores"] = sorted(list(scores_found), reverse=True)[:3]

    return result


def merge_car_with_original(original_facts: dict, car_facts: dict) -> dict:
    """
    合并汽车专用提取结果和原始结果
    """
    merged = {}

    # 合并列表字段
    for key in ["products", "prices", "specs", "brands", "scores"]:
        orig = set(original_facts.get(key, []))
        new = set(car_facts.get(key, []))
        merged[key] = sorted(list(orig | new))

    # 限制数量
    merged["products"] = merged["products"][:15]
    merged["prices"] = merged["prices"][:6]
    merged["specs"] = merged["specs"][:10]
    merged["brands"] = merged["brands"][:10]
    merged["scores"] = merged["scores"][:3]

    # 兼容旧接口
    merged["dates"] = original_facts.get("dates", [])
    merged["data"] = merged["specs"]  # 兼容data字段
    merged["recommendations"] = original_facts.get("recommendations", [])

    return merged


def is_car_query(query: str) -> bool:
    """
    判断查询是否与汽车相关
    """
    car_keywords = [
        "车", "汽车", "轿车", "SUV", "MPV", "越野",
        "新能源", "纯电", "电动", "混动", "插混", "增程",
        "续航", "充电", "电池", "充电桩", "快充", "慢充",
        "比亚迪", "特斯拉", "蔚来", "小鹏", "理想", "极氪", "问界", "小米", "埃安",
        "大众ID", "宝马i", "奔驰EQ", "奥迪e-tron",
        "买车", "购车", "选车", "提车", "落地价", "裸车价", "指导价",
        "性价比", "最值得买", "推荐", "排行榜",
    ]

    query_lower = query.lower()
    return any(kw in query_lower for kw in car_keywords)