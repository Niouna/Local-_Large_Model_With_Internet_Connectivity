"""
天气查询模块
"""

from .weather_api import WeatherAPI, create_weather_api
from .data_loader import ProvinceDataLoader, Province, City
from .city_matcher import CityMatcher, MatchResult
from .page_parser import (
    WeatherPageParser,
    ParsedWeather,
    CurrentWeather,
    DayForecast,
    HourForecast
)
from .weather_fetcher import WeatherFetcher
from .formatter import WeatherFormatter

__all__ = [
    'WeatherAPI',
    'create_weather_api',
    'ProvinceDataLoader',
    'Province',
    'City',
    'CityMatcher',
    'MatchResult',
    'WeatherPageParser',
    'ParsedWeather',
    'CurrentWeather',
    'DayForecast',
    'HourForecast',
    'WeatherFetcher',
    'WeatherFormatter',
]