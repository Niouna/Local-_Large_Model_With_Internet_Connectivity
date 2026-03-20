# search_module/__init__.py
"""搜索模块"""

from .search_api import SearchAPI, search
from .config import DOMAIN_TRUST_LEVELS, FILTER_THRESHOLDS
from .enhanced_search_api import search_enhanced
from .car_extractor import is_car_query  # 可选导出

__all__ = ['SearchAPI', 'search', 'DOMAIN_TRUST_LEVELS',
           'FILTER_THRESHOLDS', 'search_enhanced', 'is_car_query']