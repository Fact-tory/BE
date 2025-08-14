"""설정 관리 모듈"""

import os
from typing import Optional, List
from pydantic import Field
from pydantic_settings import BaseSettings


class CrawlerSettings(BaseSettings):
    """크롤러 설정"""
    
    # 서버 설정
    host: str = Field(default="0.0.0.0", description="FastAPI 서버 호스트")
    port: int = Field(default=8000, description="FastAPI 서버 포트")
    debug: bool = Field(default=False, description="디버그 모드")
    
    # RabbitMQ 설정
    rabbitmq_url: str = Field(default="amqp://localhost:5672", description="RabbitMQ 연결 URL")
    rabbitmq_enabled: bool = Field(default=True, description="RabbitMQ 사용 여부")
    
    # Java 백엔드 연결 설정
    java_backend_url: str = Field(default="http://localhost:8080", description="Java 백엔드 URL")
    java_backend_health_check: str = Field(default="/actuator/health", description="Java 백엔드 헬스체크 경로")
    java_backend_timeout: int = Field(default=5, description="Java 백엔드 연결 타임아웃 (초)")
    
    # 크롤링 설정
    max_concurrent_crawls: int = Field(default=3, description="최대 동시 크롤링 수")
    crawling_delay: float = Field(default=0.5, description="요청 간 지연시간 (초)")
    page_timeout: int = Field(default=30000, description="페이지 로딩 타임아웃 (ms)")
    
    # 브라우저 설정
    browser_headless: bool = Field(default=True, description="헤드리스 브라우저 사용")
    browser_args: List[str] = Field(
        default=[
            "--no-sandbox",
            "--disable-dev-shm-usage", 
            "--disable-blink-features=AutomationControlled",
            "--disable-web-security"
        ],
        description="브라우저 실행 인자"
    )
    
    # 로깅 설정
    log_level: str = Field(default="INFO", description="로그 레벨")
    log_format: str = Field(default="json", description="로그 포맷 (json/text)")
    
    # 저장 설정
    output_dir: str = Field(default="./output", description="크롤링 결과 저장 디렉토리")
    save_raw_data: bool = Field(default=True, description="원시 데이터 저장 여부")
    
    class Config:
        env_file = ".env"
        env_file_encoding = "utf-8"
        case_sensitive = False


# 전역 설정 인스턴스
settings = CrawlerSettings()