"""데이터 모델 정의"""

from datetime import datetime
from typing import Dict, List, Optional, Any
from pydantic import BaseModel, Field


class NaverCrawlingRequest(BaseModel):
    """네이버 크롤링 요청 모델"""
    session_id: str = Field(alias="sessionId")
    office_id: str = Field(alias="officeId")
    category_id: str = Field(alias="categoryId") 
    max_articles: int = Field(default=100, alias="maxArticles")
    include_content: bool = Field(default=True, alias="includeContent")


class RawNewsData(BaseModel):
    """원시 뉴스 데이터 모델"""
    title: str
    content: Optional[str] = None
    url: str
    original_url: Optional[str] = Field(None, alias="originalUrl")
    author_name: Optional[str] = Field(None, alias="authorName")
    published_at: Optional[str] = Field(None, alias="publishedAt")
    discovered_at: str = Field(alias="discoveredAt")
    source: str = "python_crawler"
    category_id: Optional[str] = Field(None, alias="categoryId")
    metadata: Dict[str, Any] = Field(default_factory=dict)


class CrawlingProgress(BaseModel):
    """크롤링 진행상황 모델"""
    session_id: str = Field(alias="sessionId")
    status: str  # started, crawling, processing, completed, failed
    progress: int  # 0-100
    message: str
    total_articles: int = Field(alias="totalArticles")
    processed_articles: int = Field(alias="processedArticles") 
    success_count: int = Field(alias="successCount")
    fail_count: int = Field(alias="failCount")
    timestamp: datetime = Field(default_factory=datetime.now)


class CrawlingResult(BaseModel):
    """크롤링 결과 모델"""
    session_id: str
    success: bool
    total_requested: int
    total_found: int
    total_processed: int
    success_count: int
    fail_count: int
    data: List[RawNewsData]
    errors: List[str] = Field(default_factory=list)
    start_time: datetime
    end_time: datetime
    duration_seconds: float


class CrawlingStatus(BaseModel):
    """크롤링 상태 모델"""
    active_sessions: List[str] = Field(default_factory=list)
    total_sessions: int = 0
    java_backend_connected: bool = False
    rabbitmq_connected: bool = False
    system_status: str = "running"  # running, error, maintenance


# RabbitMQ 메시지 모델들
class CrawlingRequestMessage(BaseModel):
    request_id: str = Field(alias="requestId")
    request_type: str = Field(alias="requestType")
    payload: NaverCrawlingRequest
    timestamp: int


class CrawlingResultMessage(BaseModel):
    request_id: str = Field(alias="requestId")
    success: bool
    data: Optional[List[RawNewsData]] = None
    error_message: Optional[str] = Field(None, alias="errorMessage")
    timestamp: int


class CrawlingErrorMessage(BaseModel):
    request_id: str = Field(alias="requestId")
    error_message: str = Field(alias="errorMessage")
    error_type: str = Field(alias="errorType")
    timestamp: int