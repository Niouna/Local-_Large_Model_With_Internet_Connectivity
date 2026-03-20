"""
本地天气数据加载模块
从province_code目录加载省份和城市数据
"""

import json
from pathlib import Path
from typing import Dict, List, Optional
from dataclasses import dataclass


@dataclass
class Province:
    code: str  # AHB
    name: str  # 湖北省
    url: str  # /publish/forecast/AHB.html


@dataclass
class City:
    code: str  # bSpCz（随机ID）
    province_name: str  # 湖北省
    name: str  # 武汉
    url: str  # /publish/forecast/AHB/wuhan.html
    province_code: str  # AHB
    pinyin: str  # wuhan（从url提取）


class ProvinceDataLoader:
    """省份数据加载器"""

    def __init__(self, data_dir: Path):
        self.data_dir = Path(data_dir)
        self._provinces: Optional[List[Province]] = None
        self._cities: Dict[str, List[City]] = {}  # province_code -> cities

    def load_provinces(self) -> List[Province]:
        """加载所有省份"""
        if self._provinces is not None:
            return self._provinces

        province_file = self.data_dir / "province"
        if not province_file.exists():
            raise FileNotFoundError(f"省份文件不存在: {province_file}")

        with open(province_file, 'r', encoding='utf-8') as f:
            data = json.load(f)

        self._provinces = [
            Province(
                code=item["code"],
                name=item["name"],
                url=item["url"]
            )
            for item in data
        ]

        return self._provinces

    def load_cities(self, province_code: str) -> List[City]:
        """加载指定省份的所有城市"""
        if province_code in self._cities:
            return self._cities[province_code]

        city_file = self.data_dir / province_code
        if not city_file.exists():
            return []

        with open(city_file, 'r', encoding='utf-8') as f:
            data = json.load(f)

        cities = []
        for item in data:
            # 从URL提取拼音和省份代码
            # /publish/forecast/AHB/wuhan.html
            parts = item["url"].strip('/').split('/')
            prov_code = parts[-2] if len(parts) >= 2 else ""
            pinyin = parts[-1].replace('.html', '') if parts else ""

            cities.append(City(
                code=item["code"],
                province_name=item["province"],
                name=item["city"],
                url=item["url"],
                province_code=prov_code,
                pinyin=pinyin
            ))

        self._cities[province_code] = cities
        return cities

    def load_all_cities(self) -> List[City]:
        """加载全国所有城市（较慢，用于构建索引）"""
        all_cities = []
        for prov in self.load_provinces():
            all_cities.extend(self.load_cities(prov.code))
        return all_cities

    def get_province_by_code(self, code: str) -> Optional[Province]:
        """通过代码获取省份"""
        for prov in self.load_provinces():
            if prov.code == code:
                return prov
        return None

    def get_province_by_name(self, name: str) -> Optional[Province]:
        """通过名称获取省份"""
        for prov in self.load_provinces():
            if name in prov.name or prov.name in name:
                return prov
        return None