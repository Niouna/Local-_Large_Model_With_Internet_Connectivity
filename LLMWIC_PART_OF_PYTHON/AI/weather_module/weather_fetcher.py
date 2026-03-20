"""
天气数据Selenium抓取模块
负责渲染页面并获取完整HTML
"""

import time
from typing import Optional

from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC


class WeatherFetcher:
    """天气数据抓取器"""

    def __init__(self, driver_manager):
        """
        Args:
            driver_manager: DriverManager类（你的utils.driver_manager）
        """
        self.driver_manager = driver_manager

    def fetch(self, url: str, wait_for_data: bool = True, timeout: int = 10) -> str:
        """
        抓取天气页面

        Args:
            url: 完整URL（https://www.nmc.cn/publish/forecast/...）
            wait_for_data: 是否等待动态数据加载
            timeout: 等待超时时间

        Returns:
            渲染后的HTML
        """
        driver = None
        try:
            driver = self.driver_manager.get_driver()
            driver.get(url)

            if wait_for_data:
                self._wait_for_data(driver, timeout)

            # 额外等待确保JS执行完成
            time.sleep(1)

            return driver.page_source

        finally:
            if driver:
                driver.quit()

    def _wait_for_data(self, driver, timeout: int):
        """等待动态数据加载"""
        try:
            # 等待温度元素出现且不为空
            WebDriverWait(driver, timeout).until(
                lambda d: self._has_temperature_data(d)
            )
        except Exception as e:
            # 超时也继续，可能部分数据已加载
            print(f"[WeatherFetcher] 等待数据超时: {e}")

    def _has_temperature_data(self, driver) -> bool:
        """检查是否有温度数据"""
        try:
            temp_div = driver.find_element(By.ID, "realTemperature")
            text = temp_div.text.strip()
            return text != "" and text != "&nbsp;"
        except:
            return False

    def fetch_fast(self, url: str) -> str:
        """
        快速抓取（不等待动态数据，只拿静态HTML）
        用于获取7天预报等静态内容
        """
        driver = None
        try:
            driver = self.driver_manager.get_driver()
            driver.get(url)
            time.sleep(2)  # 简单等待页面加载
            return driver.page_source
        finally:
            if driver:
                driver.quit()