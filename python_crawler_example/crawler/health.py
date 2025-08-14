"""헬스체크 및 연결 상태 관리"""

import asyncio
import logging
from typing import Optional
import aiohttp
import aio_pika
from .config import settings

logger = logging.getLogger(__name__)


class HealthChecker:
    """시스템 헬스체크 관리자"""
    
    def __init__(self):
        self.java_backend_connected = False
        self.rabbitmq_connected = False
        self._rabbitmq_connection: Optional[aio_pika.Connection] = None
        
    async def check_java_backend(self) -> bool:
        """Java 백엔드 연결 상태 확인"""
        try:
            url = f"{settings.java_backend_url}{settings.java_backend_health_check}"
            
            async with aiohttp.ClientSession(
                timeout=aiohttp.ClientTimeout(total=settings.java_backend_timeout)
            ) as session:
                async with session.get(url) as response:
                    if response.status == 200:
                        self.java_backend_connected = True
                        logger.info("Java 백엔드 연결 확인")
                        return True
                        
        except Exception as e:
            logger.warning(f"Java 백엔드 연결 실패: {e}")
            
        self.java_backend_connected = False
        return False
    
    async def check_rabbitmq(self) -> bool:
        """RabbitMQ 연결 상태 확인"""
        if not settings.rabbitmq_enabled:
            return False
            
        try:
            if self._rabbitmq_connection and not self._rabbitmq_connection.is_closed:
                self.rabbitmq_connected = True
                return True
                
            # 새로운 연결 시도
            connection = await aio_pika.connect_robust(
                settings.rabbitmq_url,
                timeout=5
            )
            
            # 연결 테스트
            channel = await connection.channel()
            await channel.close()
            
            self._rabbitmq_connection = connection
            self.rabbitmq_connected = True
            logger.info("RabbitMQ 연결 확인")
            return True
            
        except Exception as e:
            logger.warning(f"RabbitMQ 연결 실패: {e}")
            
        self.rabbitmq_connected = False
        return False
    
    async def get_connection_mode(self) -> str:
        """현재 연결 모드 반환"""
        java_ok = await self.check_java_backend()
        rabbitmq_ok = await self.check_rabbitmq() if settings.rabbitmq_enabled else False
        
        if java_ok and rabbitmq_ok:
            return "integrated"  # Java 백엔드와 연동
        elif rabbitmq_ok:
            return "queue_only"  # 큐만 사용
        else:
            return "standalone"  # 독립 실행
    
    async def periodic_health_check(self, interval: int = 30):
        """주기적 헬스체크 (백그라운드 태스크)"""
        while True:
            try:
                mode = await self.get_connection_mode()
                logger.debug(f"현재 연결 모드: {mode}")
                
                if mode == "standalone":
                    logger.info("독립 실행 모드 - 크롤링 결과를 로컬에 저장합니다")
                    
            except Exception as e:
                logger.error(f"헬스체크 에러: {e}")
                
            await asyncio.sleep(interval)
    
    async def close(self):
        """연결 정리"""
        if self._rabbitmq_connection and not self._rabbitmq_connection.is_closed:
            await self._rabbitmq_connection.close()


# 전역 헬스체커 인스턴스
health_checker = HealthChecker()