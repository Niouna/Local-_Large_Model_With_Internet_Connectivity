# utils/config.py
"""
项目配置文件
集中管理浏览器、驱动及运行参数
"""


class Config:
    # --- 浏览器配置 ---
    # 浏览器类型：目前仅支持 'edge'
    BROWSER = "edge"

    # 是否开启无头模式 (True=后台运行不显示界面, False=显示界面)
    HEADLESS = False

    # --- 驱动配置 ---
    # 隐式等待时间 (秒)，元素未加载完成时最多等待多久
    IMPLICIT_WAIT = 5

    # --- 其他配置 ---
    # 可以在这里添加其他全局配置，例如：
    # BASE_URL = "https://www.example.com"
    # TIMEOUT = 30