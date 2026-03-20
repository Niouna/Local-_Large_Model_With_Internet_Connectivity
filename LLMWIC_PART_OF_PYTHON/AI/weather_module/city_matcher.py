"""
城市匹配模块
支持：精确匹配、模糊匹配、拼音匹配、别名匹配
"""

import re
from typing import Dict, List, Optional, Callable
from dataclasses import dataclass

from .data_loader import City, ProvinceDataLoader


@dataclass
class MatchResult:
    city: City
    score: float  # 匹配分数（1.0=精确匹配，0.5=模糊匹配）
    match_type: str  # exact/pinyin/alias/fuzzy


class CityMatcher:
    """城市匹配器"""

    # 城市别名映射
    ALIASES: Dict[str, List[str]] = {
        "武汉": ["江城", "武昌", "汉口", "汉阳", "wh", "wuhan"],
        "北京": ["帝都", "京城", "北平", "bj", "beijing", "peking"],
        "上海": ["魔都", "沪", "申", "sh", "shanghai"],
        "广州": ["羊城", "穗", "花城", "gz", "guangzhou", "canton"],
        "深圳": ["鹏城", "sz", "shenzhen"],
        "成都": ["蓉城", "锦城", "cd", "chengdu"],
        "西安": ["长安", "古城", "xa", "xian"],
        "南京": ["金陵", "石头城", "nj", "nanjing", "nanking"],
        "杭州": ["杭城", "hz", "hangzhou"],
        "重庆": ["山城", "雾都", "cq", "chongqing"],
        "天津": ["津", "tj", "tianjin"],
        "苏州": ["姑苏", "sz", "suzhou"],
        "长沙": ["星城", "cs", "changsha"],
        "郑州": ["绿城", "zz", "zhengzhou"],
        "青岛": ["岛城", "qd", "qingdao"],
        "大连": ["滨城", "dl", "dalian"],
        "厦门": ["鹭岛", "xm", "xiamen", "amoy"],
        "香港": ["hk", "hongkong", "hong kong", "xianggang"],
        "澳门": ["macao", "macau", "aomen"],
        "台北": ["tp", "taipei", "taibei"],
    }

    def __init__(self, data_loader: ProvinceDataLoader):
        self.data_loader = data_loader
        self._all_cities: Optional[List[City]] = None
        self._index: Optional[Dict[str, List[City]]] = None  # 倒排索引

    def _build_index(self):
        """构建倒排索引加速查询"""
        if self._index is not None:
            return

        self._index = {}
        self._all_cities = self.data_loader.load_all_cities()

        for city in self._all_cities:
            # 索引城市名
            self._add_to_index(city.name, city)

            # 索引拼音
            self._add_to_index(city.pinyin, city)
            self._add_to_index(city.pinyin.replace('-', ''), city)  # wu-han -> wuhan

            # 索引别名
            aliases = self.ALIASES.get(city.name, [])
            for alias in aliases:
                self._add_to_index(alias, city)

    def _add_to_index(self, key: str, city: City):
        """添加到倒排索引"""
        key = self._normalize(key)
        if key not in self._index:
            self._index[key] = []
        if city not in self._index[key]:
            self._index[key].append(city)

    def _normalize(self, text: str) -> str:
        """标准化文本"""
        return text.lower().strip().replace(' ', '').replace('-', '')

    def match(self, query: str) -> Optional[MatchResult]:
        """
        匹配城市

        优先级：
        1. 精确匹配（中文名）
        2. 拼音精确匹配
        3. 别名匹配
        4. 模糊匹配（包含关系）
        """
        self._build_index()

        query_norm = self._normalize(query)

        # 1. 精确匹配（检查倒排索引）
        if query_norm in self._index:
            cities = self._index[query_norm]
            if cities:
                return MatchResult(
                    city=cities[0],
                    score=1.0,
                    match_type="exact"
                )

        # 2. 模糊匹配（遍历所有城市）
        best_match = None
        best_score = 0.0

        for city in self._all_cities:
            score = self._calculate_match_score(query_norm, city)
            if score > best_score and score >= 0.5:  # 阈值0.5
                best_score = score
                best_match = city

        if best_match:
            return MatchResult(
                city=best_match,
                score=best_score,
                match_type="fuzzy"
            )

        return None

    def _calculate_match_score(self, query: str, city: City) -> float:
        """计算匹配分数"""
        scores = []

        # 城市名匹配
        city_name_norm = self._normalize(city.name)
        if query == city_name_norm:
            scores.append(1.0)
        elif query in city_name_norm or city_name_norm in query:
            scores.append(0.8)

        # 拼音匹配
        pinyin_norm = self._normalize(city.pinyin)
        if query == pinyin_norm:
            scores.append(0.95)
        elif query in pinyin_norm or pinyin_norm in query:
            scores.append(0.7)

        # 别名匹配
        aliases = self.ALIASES.get(city.name, [])
        for alias in aliases:
            alias_norm = self._normalize(alias)
            if query == alias_norm:
                scores.append(0.9)
            elif query in alias_norm or alias_norm in query:
                scores.append(0.6)

        return max(scores) if scores else 0.0

    def search(self, query: str, limit: int = 5) -> List[MatchResult]:
        """搜索多个可能匹配的城市"""
        self._build_index()

        query_norm = self._normalize(query)
        results = []

        for city in self._all_cities:
            score = self._calculate_match_score(query_norm, city)
            if score > 0:
                results.append(MatchResult(
                    city=city,
                    score=score,
                    match_type="search"
                ))

        # 按分数排序
        results.sort(key=lambda x: x.score, reverse=True)
        return results[:limit]

    def add_alias(self, city_name: str, alias: str):
        """动态添加别名"""
        if city_name not in self.ALIASES:
            self.ALIASES[city_name] = []
        if alias not in self.ALIASES[city_name]:
            self.ALIASES[city_name].append(alias)
            # 重新构建索引
            self._index = None