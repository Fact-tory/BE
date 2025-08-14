"""네이버 뉴스 크롤러 (리팩터링 버전)"""

import asyncio
import logging
from datetime import datetime, timezone
from typing import List, Optional
from urllib.parse import urljoin, urlparse

from playwright.async_api import async_playwright, Browser, BrowserContext, Page

from .config import settings
from .models import NaverCrawlingRequest, RawNewsData

logger = logging.getLogger(__name__)


class NaverNewsCrawler:
    """네이버 뉴스 크롤러 (독립 실행 가능)"""
    
    def __init__(self):
        self.playwright = None
        self.browser: Optional[Browser] = None
        self.context: Optional[BrowserContext] = None
        
    async def initialize(self):
        """Playwright 브라우저 초기화"""
        try:
            self.playwright = await async_playwright().start()
            self.browser = await self.playwright.chromium.launch(
                headless=settings.browser_headless,
                args=settings.browser_args
            )
            
            self.context = await self.browser.new_context(
                user_agent="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            )
            
            logger.info("Playwright 브라우저 초기화 완료")
            
        except Exception as e:
            logger.error(f"Playwright 초기화 실패: {e}")
            raise
    
    async def crawl_naver_news(self, request: NaverCrawlingRequest) -> List[RawNewsData]:
        """네이버 뉴스 크롤링 실행"""
        if not self.context:
            raise RuntimeError("브라우저가 초기화되지 않았습니다")
            
        page = None
        try:
            page = await self.context.new_page()
            
            # 네이버 뉴스 언론사 페이지 URL 구성
            base_url = "https://news.naver.com/main/list.naver"
            params = {
                "mode": "LS2D",
                "mid": "shm", 
                "sid1": request.category_id,
                "sid2": "",
                "page": "1",
                "oid": request.office_id
            }
            
            url = f"{base_url}?" + "&".join([f"{k}={v}" for k, v in params.items()])
            
            logger.info(f"크롤링 시작: {url}")
            
            # 페이지 로드
            await page.goto(url, wait_until="domcontentloaded", timeout=settings.page_timeout)
            await asyncio.sleep(2)  # 페이지 안정화 대기
            
            # 기사 링크 수집
            article_links = await self._collect_article_links(page, request.max_articles)
            logger.info(f"수집된 기사 링크: {len(article_links)}개")
            
            # 각 기사 크롤링
            raw_news_list = []
            for i, link in enumerate(article_links, 1):
                try:
                    news_data = await self._crawl_single_article(page, link, request)
                    if news_data:
                        raw_news_list.append(news_data)
                    
                    logger.info(f"기사 처리: {i}/{len(article_links)} - {len(raw_news_list)}개 수집")
                    
                    # 요청 간격 준수
                    if i < len(article_links):
                        await asyncio.sleep(settings.crawling_delay)
                        
                except Exception as e:
                    logger.warning(f"기사 크롤링 실패: {link}, {e}")
                    continue
            
            logger.info(f"크롤링 완료: 총 {len(raw_news_list)}개 기사 수집")
            return raw_news_list
            
        except Exception as e:
            logger.error(f"크롤링 실행 실패: {e}")
            raise
        finally:
            if page:
                await page.close()
    
    async def _collect_article_links(self, page: Page, max_articles: int) -> List[str]:
        """기사 링크 수집"""
        article_links = []
        
        # 여러 선택자 시도 (네이버 뉴스 구조 변경 대응)
        selectors = [
            "a[href*='/main/read.naver']",
            ".newsflash_body a[href*='read.naver']",
            ".list_body a[href*='read.naver']",
            "a[href*='news.naver.com/main/read']"
        ]
        
        for selector in selectors:
            try:
                links = await page.eval_on_selector_all(
                    selector,
                    "elements => elements.map(el => el.href)"
                )
                
                # 중복 제거하며 추가
                for link in links:
                    if link and link not in article_links:
                        article_links.append(link)
                        
                if article_links:
                    logger.debug(f"선택자 '{selector}'로 {len(links)}개 링크 수집")
                    break
                    
            except Exception as e:
                logger.debug(f"선택자 '{selector}' 실패: {e}")
                continue
        
        # 스크롤하여 더 많은 기사 로드 시도
        if len(article_links) < max_articles:
            try:
                await page.evaluate("window.scrollTo(0, document.body.scrollHeight)")
                await asyncio.sleep(2)
                
                # 다시 링크 수집
                for selector in selectors:
                    try:
                        links = await page.eval_on_selector_all(
                            selector,
                            "elements => elements.map(el => el.href)"
                        )
                        
                        for link in links:
                            if link and link not in article_links and len(article_links) < max_articles:
                                article_links.append(link)
                        
                        if len(article_links) >= max_articles:
                            break
                            
                    except Exception:
                        continue
                        
            except Exception as e:
                logger.debug(f"스크롤 링크 수집 실패: {e}")
        
        # 상위 N개만 반환
        return article_links[:max_articles]
    
    async def _crawl_single_article(self, page: Page, url: str, request: NaverCrawlingRequest) -> Optional[RawNewsData]:
        """개별 기사 크롤링"""
        try:
            await page.goto(url, wait_until="domcontentloaded", timeout=15000)
            
            # 제목 추출
            title = None
            title_selectors = ["#title_area span", ".media_end_head_headline", "h2#title", ".article_title"]
            
            for selector in title_selectors:
                try:
                    title = await page.text_content(selector)
                    if title:
                        title = title.strip()
                        break
                except Exception:
                    continue
            
            if not title:
                logger.warning(f"제목을 찾을 수 없음: {url}")
                return None
            
            # 내용 추출
            content = None
            if request.include_content:
                content_selectors = [
                    "#dic_area", 
                    "#articleBodyContents", 
                    ".article_body",
                    ".news_article_body"
                ]
                
                for selector in content_selectors:
                    try:
                        content_element = await page.query_selector(selector)
                        if content_element:
                            content = await content_element.text_content()
                            if content:
                                content = self._clean_content(content)
                                break
                    except Exception:
                        continue
            
            # 기자명 추출
            author_name = None
            author_selectors = [
                ".byline_s .by_name",
                ".article_info .by em", 
                ".reporter_name",
                ".media_end_head_journalist .name"
            ]
            
            for selector in author_selectors:
                try:
                    author_element = await page.query_selector(selector)
                    if author_element:
                        author_name = await author_element.text_content()
                        if author_name:
                            author_name = author_name.strip().replace("기자", "").strip()
                            break
                except Exception:
                    continue
            
            # 언론사명 추출
            media_name = None
            media_selectors = [
                ".media_end_head_top_logo img",
                ".press_logo img",
                ".media_logo img"
            ]
            
            for selector in media_selectors:
                try:
                    media_element = await page.query_selector(selector)
                    if media_element:
                        media_name = await media_element.get_attribute("alt")
                        if media_name:
                            break
                except Exception:
                    continue
            
            # 발행 시간 추출
            published_at = None
            time_selectors = [
                ".article_info span._ARTICLE_DATE_TIME",
                ".media_end_head_info_datestamp_time",
                ".article_date"
            ]
            
            for selector in time_selectors:
                try:
                    time_element = await page.query_selector(selector)
                    if time_element:
                        published_at = await time_element.text_content()
                        if published_at:
                            published_at = published_at.strip()
                            break
                except Exception:
                    continue
            
            return RawNewsData(
                title=title,
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
    
    def _clean_content(self, content: str) -> str:
        """텍스트 정제 (최소한의 정제)"""
        if not content:
            return ""
        
        # 기본적인 공백 정리
        content = content.strip()
        
        # 줄바꿈 정리 (연속된 줄바꿈을 하나로)
        lines = []
        for line in content.split('\n'):
            line = line.strip()
            if line:  # 빈 줄이 아닌 경우만 추가
                lines.append(line)
        
        return '\n'.join(lines)
    
    async def cleanup(self):
        """리소스 정리"""
        try:
            if self.context:
                await self.context.close()
            if self.browser:
                await self.browser.close()
            if self.playwright:
                await self.playwright.stop()
                
            logger.info("브라우저 리소스 정리 완료")
            
        except Exception as e:
            logger.error(f"리소스 정리 실패: {e}")


# 편의성을 위한 함수
async def quick_crawl(office_id: str, category_id: str, max_articles: int = 10) -> List[RawNewsData]:
    """빠른 크롤링 실행 (테스트용)"""
    request = NaverCrawlingRequest(
        session_id=f"quick_{datetime.now().strftime('%Y%m%d_%H%M%S')}",
        office_id=office_id,
        category_id=category_id,
        max_articles=max_articles,
        include_content=True
    )
    
    crawler = NaverNewsCrawler()
    try:
        await crawler.initialize()
        return await crawler.crawl_naver_news(request)
    finally:
        await crawler.cleanup()