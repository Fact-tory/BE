#!/usr/bin/env python3
"""
🐍 Python 네이버 뉴스 크롤링 워커
RabbitMQ를 통해 Java 백엔드와 통신하여 크롤링 작업 수행
"""

import asyncio
import json
import logging
import sys
from datetime import datetime, timezone
from typing import Dict, List, Optional, Any
import traceback
import signal
import os

import aio_pika
import aiohttp
from playwright.async_api import async_playwright, Browser, BrowserContext, Page
from pydantic import BaseModel, Field

# 로깅 설정
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

# ==================== 데이터 모델 ====================

class NaverCrawlingRequest(BaseModel):
    session_id: str = Field(alias="sessionId")
    office_id: str = Field(alias="officeId")
    category_id: str = Field(alias="categoryId")
    max_articles: int = Field(default=100, alias="maxArticles")
    include_content: bool = Field(default=True, alias="includeContent")

class CrawlingRequestMessage(BaseModel):
    request_id: str = Field(alias="requestId")
    request_type: str = Field(alias="requestType")
    payload: NaverCrawlingRequest
    timestamp: int

class RawNewsData(BaseModel):
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

class CrawlingResultMessage(BaseModel):
    request_id: str = Field(alias="requestId")
    success: bool
    data: Optional[List[RawNewsData]] = None
    error_message: Optional[str] = Field(None, alias="errorMessage")
    timestamp: int

class CrawlingProgress(BaseModel):
    session_id: str = Field(alias="sessionId")
    status: str
    progress: int
    message: str
    total_articles: int = Field(alias="totalArticles")
    processed_articles: int = Field(alias="processedArticles")
    success_count: int = Field(alias="successCount")
    fail_count: int = Field(alias="failCount")

# ==================== 크롤링 워커 클래스 ====================

class NaverCrawlingWorker:
    def __init__(self, rabbitmq_url: str = "amqp://localhost"):
        self.rabbitmq_url = rabbitmq_url
        self.connection = None
        self.channel = None
        self.browser: Optional[Browser] = None
        self.context: Optional[BrowserContext] = None
        self.running = True
        
        # 큐 설정
        self.EXCHANGE = "crawling.exchange"
        self.REQUEST_QUEUE = "crawling.request.queue"
        self.RESULT_ROUTING_KEY = "crawling.result"
        self.PROGRESS_ROUTING_KEY = "crawling.progress"
    
    async def initialize(self):
        """RabbitMQ 연결 및 Playwright 브라우저 초기화"""
        try:
            # RabbitMQ 연결
            self.connection = await aio_pika.connect_robust(self.rabbitmq_url)
            self.channel = await self.connection.channel()
            await self.channel.set_qos(prefetch_count=1)  # 동시 처리 제한
            
            logger.info("RabbitMQ 연결 완료")
            
            # Playwright 브라우저 초기화
            self.playwright = await async_playwright().start()
            self.browser = await self.playwright.chromium.launch(
                headless=True,
                args=[
                    "--no-sandbox",
                    "--disable-dev-shm-usage",
                    "--disable-blink-features=AutomationControlled",
                    "--disable-web-security"
                ]
            )
            
            self.context = await self.browser.new_context(
                user_agent="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            )
            
            logger.info("Playwright 브라우저 초기화 완료")
            
        except Exception as e:
            logger.error(f"초기화 실패: {e}")
            raise
    
    async def start_consuming(self):
        """크롤링 요청 큐에서 메시지 소비 시작"""
        try:
            queue = await self.channel.declare_queue(self.REQUEST_QUEUE, durable=True)
            
            async with queue.iterator() as queue_iter:
                async for message in queue_iter:
                    if not self.running:
                        break
                        
                    async with message.process():
                        try:
                            await self.handle_crawling_request(message.body)
                        except Exception as e:
                            logger.error(f"메시지 처리 실패: {e}")
                            logger.error(traceback.format_exc())
        
        except Exception as e:
            logger.error(f"메시지 소비 실패: {e}")
            raise
    
    async def handle_crawling_request(self, message_body: bytes):
        """크롤링 요청 처리"""
        try:
            # 메시지 파싱
            message_data = json.loads(message_body.decode())
            request_msg = CrawlingRequestMessage(**message_data)
            
            logger.info(f"크롤링 요청 수신: {request_msg.request_id}")
            
            # 진행상황 초기화
            await self.send_progress(
                request_msg.payload.session_id,
                "started", 0, "크롤링 시작...", 0, 0, 0, 0
            )
            
            # 네이버 뉴스 크롤링 실행
            raw_news_list = await self.crawl_naver_news(request_msg.payload)
            
            # 성공 결과 전송
            result = CrawlingResultMessage(
                request_id=request_msg.request_id,
                success=True,
                data=raw_news_list,
                error_message=None,
                timestamp=int(datetime.now().timestamp() * 1000)
            )
            
            await self.send_result(result)
            
            logger.info(f"크롤링 완료: {request_msg.request_id}, 수집 기사: {len(raw_news_list)}")
            
        except Exception as e:
            logger.error(f"크롤링 요청 처리 실패: {e}")
            logger.error(traceback.format_exc())
            
            # 실패 결과 전송
            try:
                request_data = json.loads(message_body.decode())
                result = CrawlingResultMessage(
                    request_id=request_data.get("requestId", "unknown"),
                    success=False,
                    data=None,
                    error_message=str(e),
                    timestamp=int(datetime.now().timestamp() * 1000)
                )
                await self.send_result(result)
            except Exception as send_error:
                logger.error(f"실패 결과 전송 실패: {send_error}")
    
    async def crawl_naver_news(self, request: NaverCrawlingRequest) -> List[RawNewsData]:
        """네이버 뉴스 크롤링 실행"""
        page = None
        try:
            page = await self.context.new_page()
            
            # 네이버 뉴스 언론사 페이지로 이동
            url = f"https://news.naver.com/main/list.naver?mode=LS2D&mid=shm&sid1={request.category_id}&sid2=&page=1&oid={request.office_id}"
            
            await self.send_progress(
                request.session_id, "crawling", 10, 
                f"네이버 뉴스 페이지 로딩: {url}", 0, 0, 0, 0
            )
            
            await page.goto(url, wait_until="domcontentloaded", timeout=30000)
            
            # 기사 링크 수집
            article_links = []
            
            # 스크롤 및 기사 링크 수집
            for scroll_attempt in range(5):  # 최대 5번 스크롤
                # 현재 페이지의 기사 링크 수집
                links = await page.eval_on_selector_all(
                    "a[href*='/main/read.naver']", 
                    "elements => elements.map(el => el.href)"
                )
                
                for link in links:
                    if link not in article_links:
                        article_links.append(link)
                
                if len(article_links) >= request.max_articles:
                    break
                
                # 스크롤
                await page.evaluate("window.scrollTo(0, document.body.scrollHeight)")\n                await asyncio.sleep(2)
                
                await self.send_progress(
                    request.session_id, "crawling", 
                    10 + (scroll_attempt + 1) * 10,
                    f"기사 링크 수집 중... ({len(article_links)}개)",
                    len(article_links), 0, 0, 0
                )
            
            # 상위 N개 기사만 선택
            article_links = article_links[:request.max_articles]
            
            await self.send_progress(
                request.session_id, "crawling", 60,
                f"기사 내용 수집 시작... ({len(article_links)}개)",
                len(article_links), 0, 0, 0
            )
            
            # 각 기사 내용 크롤링
            raw_news_list = []
            for i, link in enumerate(article_links):
                try:
                    news_data = await self.crawl_single_article(page, link, request)
                    if news_data:
                        raw_news_list.append(news_data)
                    
                    # 진행상황 업데이트
                    progress = 60 + ((i + 1) * 35 // len(article_links))
                    await self.send_progress(
                        request.session_id, "crawling", progress,
                        f"기사 수집 중... ({i + 1}/{len(article_links)})",
                        len(article_links), i + 1, len(raw_news_list), 
                        (i + 1) - len(raw_news_list)
                    )
                    
                    await asyncio.sleep(0.5)  # 요청 간격
                    
                except Exception as e:
                    logger.warning(f"기사 크롤링 실패: {link}, {e}")
            
            await self.send_progress(
                request.session_id, "completed", 100,
                f"크롤링 완료: {len(raw_news_list)}개 수집",
                len(article_links), len(article_links), 
                len(raw_news_list), len(article_links) - len(raw_news_list)
            )
            
            return raw_news_list
            
        finally:
            if page:
                await page.close()
    
    async def crawl_single_article(self, page: Page, url: str, request: NaverCrawlingRequest) -> Optional[RawNewsData]:
        """개별 기사 크롤링"""
        try:
            await page.goto(url, wait_until="domcontentloaded", timeout=15000)
            
            # 제목 추출
            title = await page.text_content("#title_area span")
            if not title:
                return None
            
            # 내용 추출 (옵션)
            content = None
            if request.include_content:
                content_element = await page.query_selector("#dic_area, #articleBodyContents")
                if content_element:
                    content = await content_element.text_content()
                    content = self.clean_content(content) if content else None
            
            # 기자명 추출
            author_name = None
            author_element = await page.query_selector(".byline_s .by_name, .article_info .by em")
            if author_element:
                author_name = await author_element.text_content()
                if author_name:
                    author_name = author_name.strip().replace("기자", "").strip()
            
            # 언론사명 추출
            media_name = None
            media_element = await page.query_selector(".media_end_head_top_logo img")
            if media_element:
                media_name = await media_element.get_attribute("alt")
            
            # 발행 시간 추출
            published_at = None
            time_element = await page.query_selector(".article_info span._ARTICLE_DATE_TIME")
            if time_element:
                published_at = await time_element.text_content()
            
            return RawNewsData(
                title=title.strip(),
                content=content,
                url=url,
                original_url=url,
                author_name=author_name,
                published_at=published_at,
                discovered_at=datetime.now(timezone.utc).isoformat(),
                source="python_crawler",
                category_id=request.category_id,
                metadata={
                    "media_name": media_name,
                    "office_id": request.office_id,
                    "crawling_session": request.session_id
                }
            )
            
        except Exception as e:
            logger.warning(f"기사 추출 실패: {url}, {e}")
            return None
    
    def clean_content(self, content: str) -> str:
        """텍스트 정제 (최소한의 정제)"""
        if not content:
            return ""
        
        # 기본적인 공백 정리만
        content = content.strip()
        content = "\n".join(line.strip() for line in content.split("\n") if line.strip())
        
        return content
    
    async def send_result(self, result: CrawlingResultMessage):
        """크롤링 결과 전송"""
        try:
            message_body = result.json(by_alias=True).encode()
            
            await self.channel.default_exchange.publish(
                aio_pika.Message(
                    body=message_body,
                    content_type="application/json",
                    delivery_mode=aio_pika.DeliveryMode.PERSISTENT
                ),
                routing_key=self.RESULT_ROUTING_KEY
            )
            
            logger.debug(f"결과 전송 완료: {result.request_id}")
            
        except Exception as e:
            logger.error(f"결과 전송 실패: {e}")
    
    async def send_progress(self, session_id: str, status: str, progress: int, 
                          message: str, total: int, processed: int, 
                          success: int, failed: int):
        """진행상황 전송"""
        try:
            progress_msg = CrawlingProgress(
                session_id=session_id,
                status=status,
                progress=progress,
                message=message,
                total_articles=total,
                processed_articles=processed,
                success_count=success,
                fail_count=failed
            )
            
            message_body = progress_msg.json(by_alias=True).encode()
            
            await self.channel.default_exchange.publish(
                aio_pika.Message(
                    body=message_body,
                    content_type="application/json"
                ),
                routing_key=self.PROGRESS_ROUTING_KEY
            )
            
        except Exception as e:
            logger.error(f"진행상황 전송 실패: {e}")
    
    async def cleanup(self):
        """리소스 정리"""
        self.running = False
        
        if self.context:
            await self.context.close()
        if self.browser:
            await self.browser.close()
        if self.playwright:
            await self.playwright.stop()
        if self.connection:
            await self.connection.close()
        
        logger.info("리소스 정리 완료")

# ==================== 메인 실행 ====================

async def main():
    """메인 실행 함수"""
    # 환경변수에서 RabbitMQ URL 가져오기
    rabbitmq_url = os.getenv("RABBITMQ_URL", "amqp://localhost")
    
    worker = NaverCrawlingWorker(rabbitmq_url)
    
    # 신호 처리 (우아한 종료)
    def signal_handler(sig, frame):
        logger.info("종료 신호 수신, 작업 중단...")
        asyncio.create_task(worker.cleanup())
    
    signal.signal(signal.SIGINT, signal_handler)
    signal.signal(signal.SIGTERM, signal_handler)
    
    try:
        await worker.initialize()
        logger.info("Python 크롤링 워커 시작")
        await worker.start_consuming()
        
    except Exception as e:
        logger.error(f"워커 실행 실패: {e}")
        logger.error(traceback.format_exc())
    finally:
        await worker.cleanup()

if __name__ == "__main__":
    asyncio.run(main())