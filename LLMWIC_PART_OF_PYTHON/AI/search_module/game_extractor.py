# game_extractor.py
"""
游戏攻略专用信息提取
支持：二游（鸣潮/原神/崩铁/方舟）、MOBA、FPS/Tactical Shooter、RPG等
"""

import re


def extract_game_info(text: str, source: str = "", query: str = "") -> dict:
    """
    提取游戏攻略信息

    Args:
        text: 正文内容
        source: 来源URL
        query: 原始查询（用于判断游戏类型）

    Returns:
        dict: 角色、配队、装备、评分等
    """
    result = {
        "characters": [],  # 角色/英雄/干员
        "teams": [],  # 配队/阵容/卡组
        "weapons": [],  # 武器/装备/圣遗物/声骸/配件
        "ratings": [],  # 评分/梯度/T级
        "stages": [],  # 关卡/副本/深渊/地图
        "strategies": [],  # 策略/打法/关键词
        "build_codes": [],  # 改枪码/配装码（FPS特有）
        "prices": [],  # 物资价值/装备价格（搜打撤特有）
        "game_type": "通用游戏",
    }

    # 判断游戏类型
    game_type = _detect_game_type(text, query)
    result["game_type"] = game_type

    # 通用提取（所有游戏）
    _extract_common(text, result)

    # 特定游戏提取
    if game_type == "鸣潮":
        _extract_wuthering_waves(text, result)
    elif game_type == "原神":
        _extract_genshin(text, result)
    elif game_type == "崩坏星穹铁道":
        _extract_starrail(text, result)
    elif game_type == "明日方舟":
        _extract_arknights(text, result)
    elif game_type == "绝区零":
        _extract_zzz(text, result)
    elif game_type in ["英雄联盟", "王者荣耀", "DOTA2"]:
        _extract_moba(text, result)
    elif game_type in ["三角洲行动", "暗区突围", "逃离塔科夫"]:
        _extract_tactical_fps(text, game_type, result)
    elif game_type in ["CS2", "无畏契约", "Apex英雄", "PUBG"]:
        _extract_fps(text, result)
    elif game_type == "黑神话悟空":
        _extract_black_myth(text, result)
    elif game_type in ["艾尔登法环", "怪物猎人"]:
        _extract_action_rpg(text, result)

    # 去重
    for key in result:
        if isinstance(result[key], list) and key != "game_type":
            result[key] = list(set(result[key]))[:15]

    return result


def _detect_game_type(text: str, query: str) -> str:
    """检测游戏类型"""
    text_query = f"{text} {query}".lower()

    # 战术射击/FPS（优先级高，专有名词多）
    if any(kw in text_query for kw in
           ["三角洲", "delta force", "哈夫克", "gti", "阿萨拉", "乌鲁鲁", "威龙", "蜂医", "红狼", "露娜"]):
        return "三角洲行动"
    if any(kw in text_query for kw in
           ["暗区突围", "arenabreakout", "卡莫纳", "雷诺伊尔", "阿贾克斯", "多斯", "弗雷德"]):
        return "暗区突围"
    if any(kw in text_query for kw in
           ["逃离塔科夫", "塔科夫", "tarkov", "njt", "毛子", "海关", "工厂", "实验室", "储备站"]):
        return "逃离塔科夫"

    # 二游
    if any(kw in text_query for kw in ["鸣潮", "wuthering", "wuthering waves", "今州", "瑝珑"]):
        return "鸣潮"
    if any(kw in text_query for kw in
           ["原神", "genshin", "genshin impact", "提瓦特", "蒙德", "璃月", "稻妻", "须弥", "枫丹", "纳塔"]):
        return "原神"
    if any(kw in text_query for kw in ["崩坏", "星穹铁道", "starrail", "honkai", "星核", "列车组", "黑塔", "仙舟"]):
        return "崩坏星穹铁道"
    if any(kw in text_query for kw in ["明日方舟", "arknights", "罗德岛", "整合运动", "泰拉", "干员"]):
        return "明日方舟"
    if any(kw in text_query for kw in ["绝区零", "zenless", "zzz", "新艾利都", "绳匠", "狡兔屋", "维多利亚家政"]):
        return "绝区零"

    # MOBA
    if any(kw in text_query for kw in ["英雄联盟", "lol", "league of legends", "云顶", "金铲铲", "峡谷", "召唤师"]):
        return "英雄联盟"
    if any(kw in text_query for kw in ["王者荣耀", "honor of kings", "王者", "荣耀", "召唤师技能"]):
        return "王者荣耀"
    if any(kw in text_query for kw in ["dota", "刀塔", "遗迹", "ti", "国际邀请赛"]):
        return "DOTA2"

    # FPS
    if any(kw in text_query for kw in ["cs2", "csgo", "counter-strike", "反恐精英", "major", "完美平台"]):
        return "CS2"
    if any(kw in text_query for kw in ["无畏契约", "valorant", "瓦罗兰特", "瓦", "特工", "爆能器"]):
        return "无畏契约"
    if any(kw in text_query for kw in ["apex", "apex英雄", "传奇", "跳伞", "大逃杀"]):
        return "Apex英雄"
    if any(kw in text_query for kw in ["pubg", "吃鸡", "绝地求生", "大逃杀", "缩圈"]):
        return "PUBG"

    # 单机/主机
    if any(kw in text_query for kw in ["黑神话", "black myth", "悟空", "天命人", "葫芦", "影神图"]):
        return "黑神话悟空"
    if any(kw in text_query for kw in ["艾尔登法环", "elden ring", "老头环", "褪色者", "交界地", "宫崎英高"]):
        return "艾尔登法环"
    if any(kw in text_query for kw in ["怪物猎人", "monster hunter", "mh", "rise", "world", "荒野", "猫车"]):
        return "怪物猎人"

    # 默认
    return "通用游戏"


def _extract_common(text: str, result: dict):
    """通用游戏信息提取"""

    # 评分/梯度
    rating_patterns = [
        r'[Tt]([01234])[+-]?',
        r'([SsAaBbCcDd])[+-]?级?',
        r'([SsAaBbCcDd])[+-]?档?',
        r'([SsAaBbCcDd])[+-]?梯?',
        r'([SsAaBbCcDd])[+-]? tier',
        r'tier\s*([01234])',
        r'([SsAaBbCc])[+-]?级',
    ]

    for pattern in rating_patterns:
        matches = re.findall(pattern, text, re.IGNORECASE)
        for m in matches:
            if isinstance(m, str) and m.upper() in ['S', 'A', 'B', 'C', 'D', 'T']:
                if m.upper() == 'T':
                    result["ratings"].append(f"T{m}")
                else:
                    result["ratings"].append(f"{m.upper()}级")
            elif m in ['0', '1', '2', '3', '4']:
                result["ratings"].append(f"T{m}")

    # 关卡/副本/模式
    stage_patterns = [
        r'(?:深塔|深渊|混沌|虚构|末日|危机合约|肉鸽|活动|主线|支线|周本|日常|深境螺旋)\s*([0-9]+[-]?[0-9]*)',
        r'([0-9]+[-][0-9]+)\s*(?:层|关|图|阶段|面)',
        r'第\s*([0-9]+)\s*层',
        r'([0-9]+)\s*层\s*(?:深塔|深渊|混沌)',
    ]

    for pattern in stage_patterns:
        matches = re.findall(pattern, text)
        for m in matches:
            result["stages"].append(str(m))

    # 通用策略关键词
    strategy_keywords = [
        "速通", "挂机", "自动", "手动", "低配", "高配", "平民", "氪金", "零氪", "月卡",
        "爆发", "持续", "控制", "辅助", "治疗", "护盾", "增伤", "减抗", "破防",
        "对单", "对群", "单体", "群体", "AOE", "dot", "debuff", "buff",
        "循环", "轴", "手法", "连招", "技能", "天赋", "命座", "星魂", "潜能", "天赋树",
        "开荒", "毕业", "养成", "刷取", "farm", "肝", "佛系",
    ]

    for kw in strategy_keywords:
        if kw in text:
            result["strategies"].append(kw)


def _extract_wuthering_waves(text: str, result: dict):
    """鸣潮特定提取"""

    characters = [
        "漂泊者", "男主", "女主", "女漂", "男漂", "风主", "衍射主", "湮灭主", "气动主",
        "散华", "白芷", "秧秧", "炽霞", "桃祈", "丹瑾", "秋水", "渊武", "莫特斐",
        "忌炎", "吟霖", "今汐", "长离", "相里要", "折枝", "守岸人", "椿",
        "珂莱塔", "洛可可", "菲比", "赞妮", "布兰特", "鲁帕",
        "琳奈", "爱弥斯", "奥古", "尤诺", "千咲", "嘉贝", "夏空",
    ]

    for char in characters:
        if char in text:
            result["characters"].append(char)

    # 配队组合
    team_patterns = [
        r'(?:爱|琳|莫|散|安|椿|船|柯|卡|夏|千|尤|奥|嘉|布|鲁)?(?:琳|奈|莫|散|安|暗|椿|船|柯|卡|夏|千|尤|奥|嘉|布|鲁|菲|赞|洛|珂|守|长|今|相|折|忌|吟|布|鲁)',
    ]

    # 简化：直接匹配常见配队简称
    common_teams = ["爱琳莫", "安散", "暗散", "椿散", "船散", "柯散", "卡夏千", "奥古", "尤诺", "千咲", "嘉贝"]
    for team in common_teams:
        if team in text:
            result["teams"].append(team)

    # 声骸/武器
    weapon_keywords = ["声骸", "套装", "词条", "主词条", "副词条", "暴击", "爆伤", "攻击", "防御", "生命",
                       "共鸣效率", "属性伤害", "湮灭", "热熔", "气动", "导电", "冷凝", "衍射", "光噪"]
    for kw in weapon_keywords:
        if kw in text:
            result["weapons"].append(kw)


def _extract_genshin(text: str, result: dict):
    """原神特定提取"""

    characters = [
        "旅行者", "空", "荧", "风主", "岩主", "雷主", "草主", "水主", "火主",
        "安柏", "凯亚", "丽莎", "芭芭拉", "琴", "迪卢克", "雷泽", "温迪", "可莉",
        "钟离", "甘雨", "胡桃", "魈", "达达利亚", "公子", "阿贝多", "优菈", "万叶", "枫原万叶",
        "神里绫华", "宵宫", "雷电将军", "雷神", "心海", "珊瑚宫心海", "一斗", "荒泷一斗",
        "申鹤", "八重神子", "神里绫人", "夜兰", "久岐忍",
        "赛诺", "妮露", "纳西妲", "草神", "散兵", "流浪者", "艾尔海森", "迪希雅", "白术",
        "林尼", "琳妮特", "菲米尼", "莱欧斯利", "芙宁娜", "水神", "那维莱特", "娜维娅",
        "闲云", "千织", "阿蕾奇诺", "仆人", "克洛琳德", "希格雯", "艾梅莉埃", "玛拉妮", "基尼奇", "希诺宁",
    ]

    for char in characters:
        if char in text:
            result["characters"].append(char)

    # 圣遗物
    artifacts = ["圣遗物", "套装", "主词条", "副词条", "暴击头", "爆伤头", "攻击沙", "精通沙", "充能沙", "生命沙",
                 "防御沙",
                 "元素杯", "属性杯", "攻击杯", "生命杯", "防御杯", "精通杯",
                 "攻击头", "生命头", "防御头", "治疗头", "精通头",
                 "魔女", "绝缘", "宗室", "千岩", "苍白", "追忆", "余响", "辰砂", "海染", "华馆",
                 "草套", "饰金", "乐园", "水仙", "花海", "猎人", "剧团"]
    for art in artifacts:
        if art in text:
            result["weapons"].append(art)


def _extract_starrail(text: str, result: dict):
    """崩坏星穹铁道特定提取"""

    characters = [
        "开拓者", "星", "穹", "毁灭主", "存护主", "同谐主", "记忆主", "星核精",
        "三月七", "丹恒", "姬子", "瓦尔特", "杨叔", "杰帕德", "布洛妮娅", "鸭鸭", "克拉拉", "彦卿", "白露",
        "景元", "希儿", "银狼", "罗刹", "刃", "卡芙卡", "妈妈", "符玄", " typeAliases", "饮月", "丹恒·饮月",
        "镜流", "托帕", "账账", "藿藿", "银枝", "阮梅", "真理医生", "义父",
        "黑天鹅", "花火", "黄泉", "砂金", "知更鸟", "周日哥", "波提欧", "流萤", "萨姆",
        "翡翠", "云璃", "椒丘", "飞霄", "大捷将军", "灵砂", "乱破", "星期日", "忘归人", "大黑塔", "阿格莱雅",
    ]

    for char in characters:
        if char in text:
            result["characters"].append(char)

    # 光锥/遗器
    cones = ["光锥", "专武", "遗器", "内圈", "外圈", "主词条", "副词条", "暴击", "爆伤", "速度", "击破特攻", "效果命中",
             "效果抵抗", "生命值", "攻击力", "防御力"]
    for c in cones:
        if c in text:
            result["weapons"].append(c)


def _extract_arknights(text: str, result: dict):
    """明日方舟特定提取"""

    # 六星干员（部分常用）
    operators = [
        "能天使", "银灰", "塞雷娅", "艾雅法拉", "小羊", "伊芙利特", "龙", "推进之王", "推王",
        "闪灵", "夜莺", "星熊", "安洁莉娜", "洁哥", "陈", "陈sir", "黑", "赫拉格", "老爷子",
        "麦哲伦", "莫斯提马", "小莫", "煌", "阿", "年", "刻俄柏", "小刻", "风笛",
        "傀影", "温蒂", "早露", "铃兰", "棘刺", "史尔特尔", "42", "42姐", "瑕光", "泥岩",
        "山", "空弦", "嵯峨", "异客", "凯尔希", "老女人", "红蒂", "浊心斯卡蒂", "帕拉斯",
        "水月", "琴柳", "远牙", "焰尾", "耀骑士临光", "临光", "灵知", "老鲤", "令", "澄闪", "粉毛",
        "菲亚梅塔", "肥鸭", "号角", "艾丽妮", "归溟幽灵鲨", "鲨鲨", "黑键", "多萝西", "百炼嘉维尔", "鸿雪",
        "玛恩纳", "叔叔", "白铁", "斥罪", "缄默德克萨斯", "异德", "伺夜", "焰影苇草", "林", "重岳", "大哥",
        "仇白", "麒麟R夜刀", "伊内丝", "霍尔海雅", "缪尔赛思", "圣约送葬人", "提丰", "涤火杰西卡",
        "锏", "薇薇安娜", "塑心", "阿尔图罗", "图图", "莱伊", "黍", "左乐", "艾拉", "阿斯卡纶",
    ]

    for op in operators:
        if op in text:
            result["characters"].append(op)

    # 关卡类型
    stages = ["主线", "剿灭", "危机合约", "肉鸽", "集成战略", "保全派驻", "生息演算", "引航者试炼", "连锁竞赛"]
    for s in stages:
        if s in text:
            result["stages"].append(s)


def _extract_zzz(text: str, result: dict):
    """绝区零特定提取"""

    agents = [
        "比利", "妮可", "安比", "猫又", "可琳", "安东", "本", "苍角", "珂蕾妲", "露西",
        "11号", "格莉丝", "丽娜", "莱卡恩", "狼哥", "猫宫又奈", "珂蕾妲", "本·比格",
        "艾莲", "鲨鱼妹", "朱鸢", "青衣", "简", "赛斯", "凯撒", "柏妮思", "柳", "雅", "星见雅",
        "悠真", "浅羽悠真", "耀嘉音", "伊芙琳", "雨果", "薇薇安",
    ]

    for agent in agents:
        if agent in text:
            result["characters"].append(agent)

    # 驱动盘/音擎
    gear = ["驱动盘", "音擎", "套装", "主词条", "副词条", "暴击", "爆伤", "攻击力", "防御力", "生命值", "异常精通",
            "异常掌控", "能量自动回复", "穿透值", "电伤", "火伤", "冰伤", "物理伤害", "以太伤害"]
    for g in gear:
        if g in text:
            result["weapons"].append(g)


def _extract_moba(text: str, result: dict):
    """MOBA类游戏提取"""

    # 位置
    positions = ["上单", "打野", "中单", "射手", "ADC", "辅助", "SUP", "MID", "JUG", "JGL", "TOP"]
    for pos in positions:
        if pos in text:
            result["strategies"].append(f"位置:{pos}")

    # 装备类型
    items = ["神话装", "传说装", "史诗装", "打野刀", "辅助装", "眼石", "鞋子", "多兰", "出门装", "核心装", "六神装"]
    for item in items:
        if item in text:
            result["weapons"].append(item)

    # 召唤师技能/符文
    spells = ["闪现", "点燃", "传送", "TP", "治疗", "虚弱", "净化", "惩戒", "疾跑",
              "征服者", "强攻", "致命节奏", "迅捷步法", "不灭之握", "余震", "电刑", "黑暗收割"]
    for spell in spells:
        if spell in text:
            result["strategies"].append(spell)


def _extract_tactical_fps(text: str, game_name: str, result: dict):
    """战术射击/FPS深度提取（三角洲、暗区、塔科夫）"""

    # ===== 改枪/配件系统 =====
    # 改枪码：通常是6-12位字母数字组合，或带分隔符
    build_code_patterns = [
        r'(?:改枪码|配装码|分享码|方案码|代码)[：:]?\s*([A-Z0-9]{6,16})',
        r'(?:改枪码|配装码|分享码|方案码|代码)[：:]?\s*([A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4})',
        r'\b([A-Z0-9]{8,12})\b',  # 兜底匹配
    ]

    for pattern in build_code_patterns:
        matches = re.findall(pattern, text, re.IGNORECASE)
        for m in matches:
            if len(str(m)) >= 6:
                result["build_codes"].append(str(m))

    if result["build_codes"]:
        result["strategies"].append("改枪配装")

    # 配件类型
    attachments = [
        "枪口", "枪管", "护木", " handguard", "握把", "grip", "枪托", "stock",
        "弹匣", "mag", "瞄具", "瞄准镜", "scope", "sight",
        "消音器", "suppressor", "silencer", "制退器", "compensator", "补偿器",
        "垂直握把", "三角握把", "angled grip", "激光指示器", "laser", "战术手电", "flashlight",
        "侧瞄", "canted sight", "高倍镜", "红点", "red dot", "全息", "holo", "倍镜", "scope",
        "重型枪管", "轻型枪管", "短枪管", "长枪管", "枪管长度",
    ]

    for att in attachments:
        if att in text:
            result["weapons"].append(f"配件:{att}")

    # ===== 弹药系统 =====
    ammo_types = [
        "子弹", "弹药", "ammo", "round",
        "穿甲", "AP", "armor piercing", "penetration",
        "肉伤", "flesh damage", "blunt",
        "7.62x39", "7.62x51", "7.62x54", "5.56x45", "5.45x39", "9x19", "9x39", "12 gauge", "12号", ".338", " Lapua",
        "一级弹", "二级弹", "三级弹", "四级弹", "五级弹", "六级弹", "Level [123456]",
        "肉弹", "HP", "hollow point", "hunting", "hunter",
        "穿甲弹", "BP", "BT", "BS", "M995", "M855A1", "SS190", "7N31",
        "高爆", "HE", "explosive", "曳光", "tracer",
    ]

    for ammo in ammo_types:
        if ammo in text:
            result["weapons"].append(f"弹药:{ammo}")

    # 弹药等级（数字）
    ammo_levels = re.findall(r'([123456])[级档]弹', text)
    for lv in ammo_levels:
        result["weapons"].append(f"{lv}级弹")

    # ===== 装备/护甲 =====
    armor_keywords = [
        "护甲", "armor", "vest", "helmet", "头盔", "面罩", "mask", "耳机", "headset",
        "背包", "backpack", "bag", "弹挂", "chest rig", "胸挂", "rig",
        "一级甲", "二级甲", "三级甲", "四级甲", "五级甲", "六级甲", "Level [123456] armor",
        "陶瓷甲", "ceramic", "聚乙烯", "polyethylene", "装甲钢", "armor steel", "钛合金", "titanium",
        "自闭头", "耳机头", "面罩头", "飞鱼头", "阿尔金", "killa头盔",
    ]

    for armor in armor_keywords:
        if armor in text:
            result["weapons"].append(f"装备:{armor}")

    # ===== 经济/物资（搜打撤核心） =====
    # 物资价值
    value_patterns = [
        r'(\d{2,6})\s*(?:万|w|W|k|K)?\s*(?:价值|价格|售价|物资)',
        r'价值[^\d]*(\d{2,6})\s*(?:万|w|W|k|K)?',
        r'(\d{2,6})\s*万\s*(?:物资|装备|收益)',
        r'单局[^\d]*(\d{2,6})\s*(?:万|w)',
        r'赚[^\d]*(\d{2,6})\s*(?:万|w)',
    ]

    for pattern in value_patterns:
        matches = re.findall(pattern, text, re.IGNORECASE)
        for m in matches:
            val = int(m) if int(m) > 1000 else int(m) * 10000  # 处理"300万"和"3000000"
            if 10000 <= val <= 10000000:  # 1万到1000万
                result["prices"].append(f"{val // 10000}万物资价值")

    # 关键物资/大金
    loot_keywords = [
        "大金", "小红", "小金", "情报", "显卡", "GPU", "CPU", "比特币", "BTC", "私酒", "情报文件", "文件",
        "红卡", "蓝卡", "黑卡", "黄卡", "绿卡", "紫卡", "钥匙", "key", "钥匙房", "钥匙卡",
        "保险箱", "安全箱", "sicc", "文件包", "物品箱",
        "热成像", "thermal", "夜视仪", "NVG", "夜视", "t7", "信号弹", "flare", "空投", "airdrop", "飞机",
        "坦克电池", "ledx", "输血套件", "军用电缆", "芳纶纤维", "高级工具",
    ]

    for loot in loot_keywords:
        if loot in text:
            result["strategies"].append(f"物资:{loot}")

    # ===== 地图/战术 =====
    # 三角洲地图
    if game_name == "三角洲行动":
        maps = ["零号大坝", "长弓溪谷", "巴克什", "航天基地", "普坝", "机坝", "机密大坝", "绝密大坝"]
        for m in maps:
            if m in text:
                result["stages"].append(f"地图:{m}")

    # 暗区地图
    if game_name == "暗区突围":
        maps = ["农场", "山谷", "北部山区", "北山", "军港", "前线要塞", "要塞", "电视台", "矿区", "普农", "机农"]
        for m in maps:
            if m in text:
                result["stages"].append(f"地图:{m}")

    # 塔科夫地图
    if game_name == "逃离塔科夫":
        maps = ["海关", "customs", "工厂", "factory", "森林", "woods", "海岸线", "shoreline", "实验室", "lab", "实验室",
                "储备站", "reserve", "基地", "hideout", "藏身处", "灯塔", "lighthouse", "街区", "streets", "塔科夫街区"]
        for m in maps:
            if m in text:
                result["stages"].append(f"地图:{m}")

    # 通用点位
    points = ["撤离点", "extract", "出生点", "spawn", "资源点", "loot", "交战区", "pvp", "安全区", "危险区", "高危",
              "老六", "蹲撤离点"]
    for p in points:
        if p in text:
            result["stages"].append(f"点位:{p}")

    # 战术动作
    tactics = [
        "静步", "慢走", "蹲走", "趴下", "卧倒", "探头", "歪头", "peeking",
        "闪身枪", "提前枪", "pre-fire", "架枪", "hold angle", "拉枪", "跟枪", "压枪", "控枪",
        "点射", "semi", "连发", "burst", "全自动", "auto", "full auto",
        "听声辨位", "sound whoring", "看声纹", "信息", "信息位",
        "架点", "反架", "绕后", "flank", "夹击", "pincer", "劝架", "第三方",
    ]

    for tactic in tactics:
        if tactic in text:
            result["strategies"].append(f"战术:{tactic}")

    # ===== 游戏模式/难度 =====
    modes = {
        "普通模式": ["普通", "普坝", "普农", "常规", "normal"],
        "机密模式": ["机密", "机坝", "机农", "困难", "hardcore", "hard"],
        "绝密模式": ["绝密", "绝坝", "大师", "噩梦", "nightmare", "impossible"],
        "单排": ["单排", "独狼", "单人", "solo"],
        "组排": ["组排", "组队", "多人", "固定队", "squad", "team", "duo", "trio"],
        "跑刀": ["跑刀", "裸奔", "刀崽", "hatchet", "knife"],
        "全装": ["全装", "六套", "重装", "full gear", "chad"],
        "半装": ["半装", "卡战备", "凑战备"],
    }

    for mode, keywords in modes.items():
        if any(kw in text for kw in keywords):
            result["stages"].append(f"模式:{mode}")

    # ===== 任务/赛季/商人（塔科夫/三角洲） =====
    task_keywords = [
        "任务", "每日", "每周", "赛季", "通行证", "3x3", "2x3", "9格", "安全箱", "gamma", "kappa",
        "收集者", "collector", "狙击手", "sniper", "医疗兵", "medic", "后勤", "peacekeeper", "战术部门",
        "商人", "trader", "声望", "好感度", "跳蚤市场", "flea", "藏身处", "hideout", "比特币矿场", "scav", "pmc",
    ]

    for task in task_keywords:
        if task in text:
            result["strategies"].append(f"系统:{task}")


def _extract_fps(text: str, result: dict):
    """通用FPS提取（CS/Valorant/Apex/PUBG）"""

    # 枪械类型
    guns = ["步枪", "rifle", "AK", "M4", "AUG", "SG553", "Famas", "Galil",
            "冲锋枪", "SMG", "MP9", "MAC-10", "MP5", "UMP", "P90", "PP-Bizon",
            "狙击枪", "sniper", "AWP", "SSG08", "SCAR-20", "G3SG1",
            "霰弹枪", "shotgun", "新星", "Nova", "MAG-7", "连喷", "XM1014",
            "机枪", "machine gun", "内格夫", "Negev", "M249",
            "手枪", "pistol", "格洛克", "Glock", "USP", "P2000", "P250", "CZ75", "沙漠之鹰", "Deagle", "R8"]

    for gun in guns:
        if gun in text:
            result["weapons"].append(f"枪械:{gun}")

    # 地图（CS/Valorant）
    maps = ["沙二", "dust2", "米垃圾", "mirage", "小镇", "inferno", "核子危机", "nuke", "列车停放站", "train",
            "殒命大厦", "vertigo", "远古遗迹", "ancient", "阿努比斯", "anubis",
            "亚海悬城", "ascent", "源工重镇", "bind", "隐世修所", "haven", "霓虹町", "split", "莲华古城", "lotus",
            "日落之城", "sunset", "森寒冬港", "icebox", "微风岛屿", "breeze"]

    for m in maps:
        if m in text:
            result["stages"].append(f"地图:{m}")

    # 经济（CS特有）
    eco_keywords = ["eco", "半起", "force buy", "强起", "全枪全弹", "长枪局", "钢枪", "保枪", "save"]
    for eco in eco_keywords:
        if eco in text:
            result["strategies"].append(f"经济:{eco}")


def _extract_black_myth(text: str, result: dict):
    """黑神话悟空特定提取"""

    # boss
    bosses = ["二郎神", "杨戬", "大圣残躯", "石猿", "虎先锋", "寅虎", "毒敌大王", "小黄龙", "亢金龙", "妙音", "不能",
              "不净", "不空", "不白", "黄风大圣", "黄眉", "百眼魔君", "夜叉王", "红孩儿", "黑熊精", "金池长老", "幽魂",
              "大头"]
    for boss in bosses:
        if boss in text:
            result["characters"].append(f"Boss:{boss}")

    # 法术/神通
    spells = ["定身术", "安身法", "聚形散气", "铜头铁臂", "身外身法", "毫毛", "变身", "化身", "精魄"]
    for spell in spells:
        if spell in text:
            result["strategies"].append(f"法术:{spell}")

    # 棍法
    stances = ["劈棍", "立棍", "戳棍", "轻击", "重击", "切手技", "识破", "退寸", "进尺"]
    for stance in stances:
        if stance in text:
            result["weapons"].append(f"棍法:{stance}")

    # 装备
    gear = ["武器", "棍", "枪", "防具", "头冠", "衣甲", "臂甲", "腿甲", "珍玩", "葫芦", "酒", "泡酒物", "丹药", "材料"]
    for g in gear:
        if g in text:
            result["weapons"].append(g)


def _extract_action_rpg(text: str, result: dict):
    """动作RPG通用提取（老头环/怪猎）"""

    # 武器类型
    weapons = ["直剑", "大剑", "特大剑", "曲剑", "刺剑", "太刀", "打刀", "双刀", "斧", "大斧", "锤", "大锤", "枪", "戟",
               "镰刀", "鞭", "拳套", "爪",
               "法杖", "圣印记", "弓", "大弓", "弩", "盾牌", "大盾", "中盾", "小盾",
               "大剑", "太刀", "片手", "双刀", "大锤", "狩猎笛", "长枪", "铳枪", "斩斧", "盾斧", "操虫棍", "弓", "轻弩",
               "重弩"]

    for w in weapons:
        if w in text:
            result["weapons"].append(f"武器:{w}")

    # 属性
    attrs = ["力量", "敏捷", "智力", "信仰", "感应", "arcane", "vitality", "耐力", "fp", "蓝条", "绿条", "血条",
             "火属性", "雷属性", "冰属性", "龙属性", "水属性", "毒", "麻痹", "睡眠", "爆破"]
    for a in attrs:
        if a in text:
            result["strategies"].append(f"属性:{a}")


def merge_game_with_original(original_facts: dict, game_facts: dict) -> dict:
    """合并游戏提取结果"""
    merged = {}

    # 游戏特有字段
    game_fields = ["characters", "teams", "weapons", "ratings", "stages", "strategies", "build_codes", "prices"]

    for key in game_fields:
        orig = set(original_facts.get(key, []))
        new = set(game_facts.get(key, []))
        merged[key] = sorted(list(orig | new))[:15]

    # 兼容旧接口
    merged["products"] = merged["characters"]  # 兼容产品字段
    merged["dates"] = original_facts.get("dates", [])
    merged["data"] = merged["weapons"] + merged["stages"] + merged["strategies"]
    merged["recommendations"] = original_facts.get("recommendations", [])
    merged["game_type"] = game_facts.get("game_type", "未知")

    return merged


def is_game_query(query: str) -> bool:
    """判断是否为游戏类查询"""
    game_keywords = [
        # 通用
        "游戏", "攻略", "配队", "阵容", "卡组", "build", "bd", "配装",
        "角色", "英雄", "干员", "特工", "探员", "agent", "干员", "op", "operator",
        "武器", "装备", "圣遗物", "声骸", "遗器", "光锥", "配件", "改枪", "配装", "build",
        "强度", "梯度", "排行", "节奏榜", "tier list", "t0", "t1", "t2", "s级", "a级",
        "深渊", "深塔", "混沌", "虚构", "末日", "危机合约", "肉鸽", "集成战略", "深境螺旋",
        "地图", "点位", "撤离点", "资源点", "出生点", "老六位",

        # 具体游戏
        "鸣潮", "原神", "崩铁", "星穹铁道", "明日方舟", "绝区零", "zzz",
        "lol", "英雄联盟", "王者", "王者荣耀", "dota",
        "三角洲", "暗区突围", "逃离塔科夫", "塔科夫",
        "cs", "csgo", "cs2", "valorant", "无畏契约", "瓦", "apex", "pubg", "吃鸡",
        "黑神话", "悟空", "老头环", "艾尔登法环", "法环", "怪物猎人", "mh",
    ]

    query_lower = query.lower()
    return any(kw in query_lower for kw in game_keywords)