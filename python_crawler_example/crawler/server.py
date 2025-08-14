"""FastAPI 서버 - 독립 실행용"""

import asyncio
import json
import logging
import os
from contextlib import asynccontextmanager
from datetime import datetime
from pathlib import Path
from typing import List, Dict
import uuid

from fastapi import FastAPI, HTTPException, BackgroundTasks
from fastapi.responses import JSONResponse
import structlog

from .config import settings
from .models import (
    NaverCrawlingRequest,
    CrawlingResult,
    CrawlingStatus,
    RawNewsData
)
from .health import health_checker
from .naver_crawler import NaverNewsCrawler

# 로깅 설정
logging.basicConfig(
    level=getattr(logging, settings.log_level.upper()),
    format="%(asctime)s - %(name)s - %(levelname)s - %(message)s"
)
logger = structlog.get_logger(__name__)

# 활성 크롤링 세션 추적
active_sessions: Dict[str, CrawlingResult] = {}
completed_sessions: Dict[str, CrawlingResult] = {}


@asynccontextmanager
async def lifespan(app: FastAPI):
    """앱 생명주기 관리"""
    logger.info("🚀 네이버 뉴스 크롤링 서버 시작")
    
    # 출력 디렉토리 생성
    output_dir = Path(settings.output_dir)
    output_dir.mkdir(exist_ok=True)
    logger.info(f"출력 디렉토리: {output_dir.absolute()}")
    
    # 백그라운드 헬스체크 시작
    health_task = asyncio.create_task(health_checker.periodic_health_check())
    
    # 연결 모드 확인
    connection_mode = await health_checker.get_connection_mode()
    logger.info(f"연결 모드: {connection_mode}")
    
    yield
    
    # 정리 작업
    health_task.cancel()
    await health_checker.close()
    logger.info("서버 종료")


# FastAPI 앱 생성
app = FastAPI(
    title="네이버 뉴스 크롤링 API",
    description="Java 백엔드와 연동하거나 독립 실행 가능한 네이버 뉴스 크롤링 서비스",
    version="1.0.0",
    lifespan=lifespan
)


@app.get("/")
async def root():
    """루트 엔드포인트"""
    return {
        "service": "네이버 뉴스 크롤링 API",
        "version": "1.0.0",
        "status": "running"
    }


@app.get("/health")
async def health_check():
    """헬스체크 엔드포인트"""
    connection_mode = await health_checker.get_connection_mode()
    
    return CrawlingStatus(
        active_sessions=list(active_sessions.keys()),
        total_sessions=len(completed_sessions),
        java_backend_connected=health_checker.java_backend_connected,
        rabbitmq_connected=health_checker.rabbitmq_connected,
        system_status=connection_mode
    )


@app.post("/crawl/naver", response_model=CrawlingResult)
async def crawl_naver_news(
    request: NaverCrawlingRequest,
    background_tasks: BackgroundTasks
):
    """네이버 뉴스 크롤링 실행"""
    
    # 세션 ID 생성 (없으면)
    if not request.session_id:
        request.session_id = str(uuid.uuid4())
    
    session_id = request.session_id
    
    # 중복 세션 체크
    if session_id in active_sessions:
        raise HTTPException(
            status_code=409,
            detail=f"세션 {session_id}가 이미 실행 중입니다"
        )
    
    logger.info(f"크롤링 요청 수신: session_id={session_id}")
    
    # 크롤링 결과 초기화
    start_time = datetime.now()
    result = CrawlingResult(
        session_id=session_id,
        success=False,
        total_requested=request.max_articles,
        total_found=0,
        total_processed=0,
        success_count=0,
        fail_count=0,
        data=[],
        start_time=start_time,
        end_time=start_time,
        duration_seconds=0.0
    )
    
    active_sessions[session_id] = result
    
    # 백그라운드에서 크롤링 실행
    background_tasks.add_task(
        execute_crawling,
        request,
        result
    )
    
    return result


@app.get("/sessions/{session_id}", response_model=CrawlingResult)
async def get_session_result(session_id: str):
    """크롤링 세션 결과 조회"""
    
    # 활성 세션 확인
    if session_id in active_sessions:
        return active_sessions[session_id]
    
    # 완료된 세션 확인
    if session_id in completed_sessions:
        return completed_sessions[session_id]
    
    raise HTTPException(
        status_code=404,
        detail=f"세션 {session_id}를 찾을 수 없습니다"
    )


@app.get("/sessions", response_model=List[str])
async def list_sessions():
    """모든 세션 목록 조회"""
    all_sessions = list(active_sessions.keys()) + list(completed_sessions.keys())
    return sorted(all_sessions, reverse=True)


@app.delete("/sessions/{session_id}")
async def delete_session(session_id: str):
    """세션 삭제 (완료된 세션만)"""
    if session_id in active_sessions:
        raise HTTPException(
            status_code=409,
            detail="실행 중인 세션은 삭제할 수 없습니다"
        )
    
    if session_id in completed_sessions:
        del completed_sessions[session_id]
        return {"message": f"세션 {session_id} 삭제 완료"}
    
    raise HTTPException(
        status_code=404,
        detail=f"세션 {session_id}를 찾을 수 없습니다"
    )


async def execute_crawling(request: NaverCrawlingRequest, result: CrawlingResult):
    """크롤링 실행 (백그라운드 태스크)"""
    session_id = request.session_id
    
    try:
        logger.info(f"크롤링 시작: {session_id}")
        
        # 크롤러 생성 및 실행
        crawler = NaverNewsCrawler()
        await crawler.initialize()
        
        try:
            # 크롤링 실행
            raw_news_list = await crawler.crawl_naver_news(request)
            
            # 결과 업데이트
            end_time = datetime.now()
            result.success = True
            result.total_found = len(raw_news_list)
            result.total_processed = len(raw_news_list)
            result.success_count = len(raw_news_list)
            result.data = raw_news_list
            result.end_time = end_time
            result.duration_seconds = (end_time - result.start_time).total_seconds()
            
            logger.info(f"크롤링 완료: {session_id}, 수집: {len(raw_news_list)}개")
            
            # 로컬 저장
            if settings.save_raw_data:
                await save_crawling_result(result)
            
        finally:
            await crawler.cleanup()
            
    except Exception as e:
        logger.error(f"크롤링 실패: {session_id}, {e}")
        
        end_time = datetime.now()
        result.success = False
        result.errors.append(str(e))
        result.end_time = end_time
        result.duration_seconds = (end_time - result.start_time).total_seconds()
    
    finally:
        # 세션 이동 (활성 → 완료)
        if session_id in active_sessions:
            completed_sessions[session_id] = active_sessions.pop(session_id)


async def save_crawling_result(result: CrawlingResult):
    """크롤링 결과를 로컬 파일로 저장"""
    try:
        output_dir = Path(settings.output_dir)
        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        filename = f"crawling_{result.session_id}_{timestamp}.json"
        filepath = output_dir / filename
        
        # 결과 저장
        with open(filepath, 'w', encoding='utf-8') as f:
            json.dump(
                result.model_dump(by_alias=True),
                f,
                ensure_ascii=False,
                indent=2,
                default=str
            )
        
        logger.info(f"크롤링 결과 저장: {filepath}")
        
    except Exception as e:
        logger.error(f"결과 저장 실패: {e}")


def main():
    """서버 실행 진입점"""
    import uvicorn
    
    uvicorn.run(
        "crawler.server:app",
        host=settings.host,
        port=settings.port,
        reload=settings.debug,
        log_level=settings.log_level.lower()
    )


if __name__ == "__main__":
    main()