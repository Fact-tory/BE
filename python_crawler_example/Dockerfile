FROM python:3.11-slim

# 시스템 패키지 설치
RUN apt-get update && apt-get install -y \
    curl \
    wget \
    gnupg \
    && rm -rf /var/lib/apt/lists/*

# Poetry 설치
ENV POETRY_HOME="/opt/poetry" \
    POETRY_CACHE_DIR="/opt/poetry/cache" \
    POETRY_VENV_IN_PROJECT=1 \
    POETRY_NO_INTERACTION=1

RUN curl -sSL https://install.python-poetry.org | python3 -
ENV PATH="$POETRY_HOME/bin:$PATH"

# 작업 디렉토리 설정
WORKDIR /app

# Poetry 설정 파일 복사
COPY pyproject.toml poetry.lock* ./

# 의존성 설치
RUN poetry install --only=main --no-dev

# Playwright 브라우저 설치
RUN poetry run playwright install chromium
RUN poetry run playwright install-deps

# 애플리케이션 코드 복사
COPY . .

# 출력 디렉토리 생성
RUN mkdir -p /app/output

# 포트 노출
EXPOSE 8000

# 헬스체크
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD curl -f http://localhost:8000/health || exit 1

# 기본 명령어 (서버 실행)
CMD ["poetry", "run", "python", "-m", "crawler.server"]

# 다양한 실행 방법을 위한 라벨
LABEL crawler.mode.server="poetry run python -m crawler.server"
LABEL crawler.mode.worker="poetry run python -m crawler.worker" 
LABEL crawler.mode.cli="poetry run python -m crawler.main"