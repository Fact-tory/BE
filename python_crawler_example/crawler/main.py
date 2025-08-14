"""CLI 메인 진입점"""

import asyncio
import json
import sys
from pathlib import Path
from typing import Optional

import click
import structlog

from .config import settings
from .naver_crawler import quick_crawl
from .server import main as server_main

# 로깅 설정
logger = structlog.get_logger(__name__)


@click.group()
@click.option('--debug', is_flag=True, help='디버그 모드 활성화')
@click.option('--config', type=click.Path(exists=True), help='설정 파일 경로')
def cli(debug: bool, config: Optional[str]):
    """네이버 뉴스 크롤링 도구"""
    if debug:
        settings.debug = True
        settings.log_level = "DEBUG"
    
    if config:
        # 설정 파일 로드 (구현 필요시)
        pass


@cli.command()
@click.option('--host', default="0.0.0.0", help='서버 호스트')
@click.option('--port', default=8000, help='서버 포트')
def server(host: str, port: int):
    """FastAPI 서버 실행"""
    settings.host = host
    settings.port = port
    server_main()


@cli.command()
@click.argument('office_id')
@click.argument('category_id')
@click.option('--max-articles', '-n', default=10, help='최대 수집 기사 수')
@click.option('--output', '-o', type=click.Path(), help='결과 저장 파일 경로')
@click.option('--format', 'output_format', default='json', 
              type=click.Choice(['json', 'text']), help='출력 형식')
def crawl(office_id: str, category_id: str, max_articles: int, 
          output: Optional[str], output_format: str):
    """네이버 뉴스 크롤링 실행
    
    OFFICE_ID: 언론사 코드 (예: 001=연합뉴스, 020=동아일보)
    CATEGORY_ID: 카테고리 코드 (예: 100=정치, 101=경제, 102=사회, 103=문화)
    """
    
    async def run_crawl():
        try:
            click.echo(f"🕷️  크롤링 시작: 언론사={office_id}, 카테고리={category_id}")
            
            results = await quick_crawl(office_id, category_id, max_articles)
            
            click.echo(f"✅ 크롤링 완료: {len(results)}개 기사 수집")
            
            # 결과 출력/저장
            if output_format == 'json':
                output_data = [result.model_dump(by_alias=True) for result in results]
                json_str = json.dumps(output_data, ensure_ascii=False, indent=2, default=str)
                
                if output:
                    Path(output).write_text(json_str, encoding='utf-8')
                    click.echo(f"📁 결과 저장: {output}")
                else:
                    click.echo(json_str)
                    
            else:  # text format
                for i, result in enumerate(results, 1):
                    text_output = f"""
=== 기사 {i} ===
제목: {result.title}
기자: {result.author_name or 'N/A'}
URL: {result.url}
내용 미리보기: {(result.content or '')[:100]}...
언론사: {result.metadata.get('media_name', 'N/A')}
---
"""
                    if output:
                        Path(output).write_text(text_output, encoding='utf-8', 
                                              mode='a' if i > 1 else 'w')
                    else:
                        click.echo(text_output)
                
                if output:
                    click.echo(f"📁 결과 저장: {output}")
            
        except Exception as e:
            click.echo(f"❌ 크롤링 실패: {e}", err=True)
            sys.exit(1)
    
    asyncio.run(run_crawl())


@cli.command()
def test():
    """크롤링 테스트 실행"""
    
    async def run_test():
        try:
            click.echo("🧪 크롤링 테스트 시작...")
            
            # 연합뉴스 정치 뉴스 3개 크롤링
            results = await quick_crawl("001", "100", 3)
            
            if results:
                click.echo(f"✅ 테스트 성공: {len(results)}개 기사 수집")
                click.echo(f"첫 번째 기사: {results[0].title}")
            else:
                click.echo("⚠️  결과 없음")
                
        except Exception as e:
            click.echo(f"❌ 테스트 실패: {e}", err=True)
            sys.exit(1)
    
    asyncio.run(run_test())


@cli.command()
def config_show():
    """현재 설정 출력"""
    config_dict = {
        "server": {
            "host": settings.host,
            "port": settings.port,
            "debug": settings.debug
        },
        "crawling": {
            "max_concurrent_crawls": settings.max_concurrent_crawls,
            "crawling_delay": settings.crawling_delay,
            "page_timeout": settings.page_timeout
        },
        "rabbitmq": {
            "url": settings.rabbitmq_url,
            "enabled": settings.rabbitmq_enabled
        },
        "java_backend": {
            "url": settings.java_backend_url,
            "timeout": settings.java_backend_timeout
        },
        "output": {
            "directory": settings.output_dir,
            "save_raw_data": settings.save_raw_data
        },
        "logging": {
            "level": settings.log_level,
            "format": settings.log_format
        }
    }
    
    click.echo(json.dumps(config_dict, indent=2, ensure_ascii=False))


if __name__ == "__main__":
    cli()