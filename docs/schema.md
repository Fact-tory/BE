-- 🏭 Factory BE 데이터베이스 스키마 (DB 사용처 및 목적별 분류)

-- ============================================================================
-- 📊 DATABASE 사용 계획
-- ============================================================================
-- MySQL 8.0+     : 메인 데이터베이스 (구조화된 데이터, 관계형 데이터)
-- Redis 7.0+     : 세션 관리, 실시간 캐시 (휘발성 데이터)
-- OpenSearch     : 검색 엔진 (뉴스 검색, 분석 결과 검색)
-- ============================================================================

-- ============================================================================
-- 👤 USER MANAGEMENT (사용자 관리) - MySQL
-- ============================================================================

-- 1. 사용자 기본 정보 (MySQL)
CREATE TABLE `users` (
`id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '사용자 고유 식별자',
`name` VARCHAR(255) NOT NULL COMMENT '사용자 실명 또는 닉네임',
`email` VARCHAR(320) NOT NULL UNIQUE COMMENT '사용자 이메일 주소',
`profile_image` TEXT NULL COMMENT '프로필 이미지 URL (AWS S3 예정)',
`user_role` ENUM('ADMIN', 'USER') NOT NULL DEFAULT 'USER' COMMENT '사용자 역할',
`created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
`updated_at` DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,
`deleted_at` DATETIME NULL COMMENT 'Soft Delete (탈퇴)',
PRIMARY KEY (`id`)
) COMMENT = 'MySQL - 사용자 기본 정보 저장';

-- 2. 소셜 계정 연동 (MySQL)
CREATE TABLE `social_account` (
`id` BIGINT NOT NULL AUTO_INCREMENT,
`user_id` BIGINT NOT NULL COMMENT '사용자 ID (users 테이블 FK)',
`provider` ENUM('KAKAO', 'GOOGLE') NOT NULL COMMENT '소셜 로그인 제공자',
`social_id` VARCHAR(255) NOT NULL COMMENT '소셜 서비스 사용자 ID',
`email` VARCHAR(320) NOT NULL COMMENT '소셜 계정 이메일',
`social_username` VARCHAR(255) NULL COMMENT '소셜 사용자명',
`created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
`updated_at` DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,
PRIMARY KEY (`id`),
FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE CASCADE,
UNIQUE KEY `uk_provider_social_id` (`provider`, `social_id`)
) COMMENT = 'MySQL - 소셜 로그인 계정 정보 (카카오, 구글)';

-- 3. 세션 관리 (MySQL 백업용, 실제는 Redis 사용)
CREATE TABLE `sessions` (
`session_id` VARCHAR(64) NOT NULL COMMENT 'Redis 세션 ID',
`user_id` VARCHAR(64) NOT NULL COMMENT 'Redis 호환 사용자 ID (String)',
`ip_address` VARCHAR(45) NULL COMMENT '접속 IP 주소',
`user_agent` TEXT NULL COMMENT '사용자 브라우저 정보',
`created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
`last_access_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
`expires_at` DATETIME NOT NULL COMMENT '세션 만료 시간',
`is_active` TINYINT NOT NULL DEFAULT 1 COMMENT '세션 활성 상태',
PRIMARY KEY (`session_id`)
) COMMENT = 'MySQL - 세션 백업 (주 저장소는 Redis)';

-- ============================================================================
-- 📰 NEWS CONTENT (뉴스 컨텐츠) - MySQL
-- ============================================================================

-- 4. 카테고리 (MySQL)
CREATE TABLE `categories` (
`id` BIGINT NOT NULL AUTO_INCREMENT,
`name` VARCHAR(100) NOT NULL COMMENT '카테고리 한글명 (정치, 경제, 사회, 문화)',
`name_en` VARCHAR(100) NOT NULL UNIQUE COMMENT '영문명 (politics, economy, society, culture)',
`description` VARCHAR(500) NOT NULL COMMENT '카테고리 설명',
`display_order` INT NOT NULL DEFAULT 1 COMMENT '화면 표시 순서',
`is_active` BOOLEAN NOT NULL DEFAULT TRUE COMMENT '활성 상태',
`created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
PRIMARY KEY (`id`)
) COMMENT = 'MySQL - 뉴스 카테고리 정보';

-- 5. 언론사 (MySQL - 크롤링 가능한 정보만)
CREATE TABLE `media_outlets` (
`id` BIGINT NOT NULL AUTO_INCREMENT,
`name` VARCHAR(255) NOT NULL COMMENT '언론사명 (RSS 제목에서 크롤링)',
`domain` VARCHAR(255) NOT NULL UNIQUE COMMENT '도메인 (URL에서 추출)',
`website` VARCHAR(500) NULL COMMENT '웹사이트 URL (크롤링 소스)',
`political_bias` ENUM('progressive', 'neutral', 'conservative') NULL DEFAULT 'neutral' COMMENT '정치 성향 (관리자 수동 설정)',
`crawling_platform` ENUM('rss', 'sitemap', 'api', 'manual', 'scraping') NULL DEFAULT 'rss',
`crawling_url` VARCHAR(1000) NULL COMMENT '실제 크롤링 URL',
`crawling_settings` JSON NULL COMMENT '크롤링 설정 (헤더, 주기 등)',
`is_active` BOOLEAN NOT NULL DEFAULT TRUE COMMENT '크롤링 활성 상태',
`created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
`updated_at` DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,
`deleted_at` DATETIME NULL,
PRIMARY KEY (`id`)
) COMMENT = 'MySQL - 언론사 정보 (크롤링 기반 수집)';

-- 6. 기자 (MySQL - 크롤링 가능한 정보만)
CREATE TABLE `journalists` (
`id` BIGINT NOT NULL AUTO_INCREMENT,
`name` VARCHAR(255) NOT NULL COMMENT '기자명 (기사 작성자에서 크롤링)',
`media_outlet_id` BIGINT NOT NULL COMMENT '소속 언론사 ID',
`is_active` BOOLEAN NOT NULL DEFAULT TRUE COMMENT '기자 활성 상태',
`created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
`updated_at` DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,
`deleted_at` DATETIME NULL,
PRIMARY KEY (`id`),
FOREIGN KEY (`media_outlet_id`) REFERENCES `media_outlets`(`id`),
UNIQUE KEY `uk_name_outlet` (`name`, `media_outlet_id`) COMMENT '언론사별 기자명 중복 방지'
) COMMENT = 'MySQL - 기자 정보 (기사에서 크롤링)';

-- 7. 뉴스 기사 (MySQL)
CREATE TABLE `news` (
`id` VARCHAR(36) NOT NULL COMMENT '뉴스 기사 UUID',
`media_outlet_id` BIGINT NOT NULL COMMENT '언론사 ID (FK)',
`journalist_id` BIGINT NULL COMMENT '기자 ID (FK, 매칭 실패시 NULL)',
`title` VARCHAR(1000) NOT NULL COMMENT '기사 제목 (크롤링)',
`content` LONGTEXT NOT NULL COMMENT '기사 본문 (크롤링)',
`url` VARCHAR(2000) NOT NULL UNIQUE COMMENT '기사 원본 URL',
`author_name` VARCHAR(255) NOT NULL COMMENT '작성자명 (크롤링, 비정규화)',
`published_at` DATETIME NOT NULL COMMENT '게시 일시 (크롤링)',
`crawled_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
`category` ENUM('POLITICS', 'ECONOMY', 'SOCIETY', 'CULTURE') NOT NULL COMMENT '카테고리 (AI 자동 분류)',
`summary` TEXT NULL COMMENT 'AI 생성 요약',
`keywords` JSON NULL COMMENT 'AI 추출 키워드 배열',
`view_count` BIGINT NOT NULL DEFAULT 0 COMMENT '조회수',
`crawling_source` ENUM('rss', 'sitemap', 'api', 'manual', 'scraping') NULL DEFAULT 'rss',
`crawling_platform` VARCHAR(100) NULL COMMENT '크롤링 플랫폼 (네이버뉴스, 다음뉴스 등)',
`status` ENUM('draft', 'published', 'archived', 'deleted') NULL DEFAULT 'published',
`created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
`updated_at` DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,
`deleted_at` DATETIME NULL,
PRIMARY KEY (`id`),
FOREIGN KEY (`media_outlet_id`) REFERENCES `media_outlets`(`id`),
FOREIGN KEY (`journalist_id`) REFERENCES `journalists`(`id`)
) COMMENT = 'MySQL - 뉴스 기사 원문 및 메타데이터';

-- ============================================================================
-- 🤖 AI ANALYSIS (AI 분석) - MySQL
-- ============================================================================

-- 8. 분석 세션 (MySQL)
CREATE TABLE `analysis_sessions` (
`session_id` VARCHAR(64) NOT NULL COMMENT '분석 세션 UUID (WebSocket 연결용)',
`user_id` BIGINT NOT NULL COMMENT '분석 요청 사용자 ID',
`news_id` VARCHAR(36) NULL COMMENT '크롤링 완료 후 생성되는 뉴스 ID',
`original_url` VARCHAR(2000) NOT NULL COMMENT '사용자 입력 원본 URL',
`status` ENUM('queued','processing','completed','failed','cancelled') NOT NULL COMMENT '분석 진행 상태',
`current_step` VARCHAR(100) NULL COMMENT '현재 진행 단계 (UI 표시용)',
`progress_percentage` INT NOT NULL DEFAULT 0 COMMENT '진행률 (0-100)',
`error_message` TEXT NULL COMMENT '오류 메시지',
`progress_logs` JSON NULL COMMENT '단계별 상세 로그',
`estimated_completion_time` DATETIME NULL COMMENT '예상 완료 시간',
`queue_position` INT NULL COMMENT '대기열 순서',
`created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
`completed_at` DATETIME NULL COMMENT '분석 완료 시간',
`deleted_at` DATETIME NULL,
PRIMARY KEY (`session_id`),
FOREIGN KEY (`user_id`) REFERENCES `users`(`id`),
FOREIGN KEY (`news_id`) REFERENCES `news`(`id`)
) COMMENT = 'MySQL - 분석 세션 관리 (진행상황 추적)';

-- 9. 분석 결과 (MySQL)
CREATE TABLE `analysis_results` (
`id` VARCHAR(36) NOT NULL COMMENT '분석 결과 UUID',
`news_id` VARCHAR(36) NOT NULL COMMENT '분석 대상 뉴스 ID',
`session_id` VARCHAR(64) NOT NULL COMMENT '분석 세션 ID',
`user_id` BIGINT NOT NULL COMMENT '분석 요청 사용자 ID',
`summary` TEXT NULL COMMENT 'AI 생성 요약',
`fact_description` TEXT NULL COMMENT '사실 내용 설명',
`bias_direction` VARCHAR(500) NULL COMMENT '편향 방향 설명',
`bias_intensity` VARCHAR(500) NULL COMMENT '편향 강도 설명',
`bias_description` TEXT NULL COMMENT '편향성 종합 설명',
`detailed_bias_description` TEXT NULL COMMENT '상세 편향성 분석',
`political_perspective` TEXT NULL COMMENT '함의된 정치적 시각',
`is_public` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '공개 분석 여부 (다른 사용자 조회 가능)',
`version` INT NOT NULL DEFAULT 1 COMMENT '분석 버전 (재분석시 증가)',
`created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
`updated_at` DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,
`deleted_at` DATETIME NULL,
PRIMARY KEY (`id`),
FOREIGN KEY (`news_id`) REFERENCES `news`(`id`),
FOREIGN KEY (`session_id`) REFERENCES `analysis_sessions`(`session_id`),
FOREIGN KEY (`user_id`) REFERENCES `users`(`id`)
) COMMENT = 'MySQL - AI 분석 결과 메인 테이블';

-- 10. 편향성 분석 상세 (MySQL)
CREATE TABLE `bias_analysis_details` (
`id` BIGINT NOT NULL AUTO_INCREMENT,
`analysis_result_id` VARCHAR(36) NOT NULL COMMENT '분석 결과 ID (FK)',
`overall_bias` ENUM('progressive','neutral','conservative') NOT NULL COMMENT '전체 편향성',
`overall_score` DECIMAL(4, 3) NOT NULL COMMENT '편향 점수 (-1.000 ~ 1.000)',
`confidence` DECIMAL(4, 3) NOT NULL DEFAULT 0.000 COMMENT 'AI 신뢰도 (0.000 ~ 1.000)',
`fact_percentage` DECIMAL(5, 2) NOT NULL COMMENT '사실 비율 (%)',
`opinion_percentage` DECIMAL(5, 2) NOT NULL COMMENT '의견 비율 (%)',
`positive_sentiment` DECIMAL(5, 2) NOT NULL COMMENT '긍정 감정 비율 (%)',
`neutral_sentiment` DECIMAL(5, 2) NOT NULL COMMENT '중립 감정 비율 (%)',
`negative_sentiment` DECIMAL(5, 2) NOT NULL COMMENT '부정 감정 비율 (%)',
`created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
`deleted_at` DATETIME NULL,
PRIMARY KEY (`id`),
FOREIGN KEY (`analysis_result_id`) REFERENCES `analysis_results`(`id`) ON DELETE CASCADE
) COMMENT = 'MySQL - 편향성 분석 상세 데이터 (1:1 관계)';

-- 11. 키워드 분석 상세 (MySQL)
CREATE TABLE `keyword_analysis_details` (
`id` BIGINT NOT NULL AUTO_INCREMENT,
`analysis_result_id` VARCHAR(36) NOT NULL COMMENT '분석 결과 ID (FK)',
`keyword` VARCHAR(255) NOT NULL COMMENT '추출된 키워드',
`frequency` INT NOT NULL COMMENT '키워드 출현 빈도',
`importance` DECIMAL(4, 3) NOT NULL DEFAULT 0.000 COMMENT '중요도 점수 (TF-IDF 기반)',
`rank` INT NOT NULL COMMENT '중요도 순위 (1위부터)',
`sentences` JSON NULL COMMENT '키워드가 포함된 문장들',
`created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
PRIMARY KEY (`id`),
FOREIGN KEY (`analysis_result_id`) REFERENCES `analysis_results`(`id`) ON DELETE CASCADE
) COMMENT = 'MySQL - 키워드 분석 상세 데이터 (1:N 관계)';

-- 12. 팩트체크 결과 (MySQL)
CREATE TABLE `fact_check_results` (
`id` BIGINT NOT NULL AUTO_INCREMENT,
`analysis_result_id` VARCHAR(36) NOT NULL COMMENT '분석 결과 ID (FK)',
`status` ENUM('verified','unverified','disputed','error') NOT NULL COMMENT '팩트체크 상태',
`message` TEXT NOT NULL COMMENT '팩트체크 결과 메시지',
`confidence` DECIMAL(4, 3) NOT NULL DEFAULT 0.000 COMMENT '팩트체크 신뢰도',
`search_result_count` INT NOT NULL DEFAULT 0 COMMENT '구글 팩트체크 검색 결과 수',
`sources` JSON NULL COMMENT '팩트체크 소스 정보 배열',
`google_factcheck_data` JSON NULL COMMENT '구글 팩트체크 API 원본 응답',
`search_keywords` JSON NULL COMMENT '팩트체크에 사용된 키워드',
`created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
`deleted_at` DATETIME NULL,
PRIMARY KEY (`id`),
FOREIGN KEY (`analysis_result_id`) REFERENCES `analysis_results`(`id`) ON DELETE CASCADE
) COMMENT = 'MySQL - 구글 팩트체크 API 결과 (1:1 관계)';

-- 13. 분석 차트 데이터 (MySQL)
CREATE TABLE `analysis_charts` (
`id` BIGINT NOT NULL AUTO_INCREMENT,
`analysis_result_id` VARCHAR(36) NOT NULL COMMENT '분석 결과 ID (FK)',
`chart_type` ENUM('sentiment_donut','keyword_treemap','network_chart','bias_scatter') NOT NULL COMMENT '차트 종류',
`chart_data` JSON NOT NULL COMMENT 'Highcharts 호환 차트 데이터',
`created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
PRIMARY KEY (`id`),
FOREIGN KEY (`analysis_result_id`) REFERENCES `analysis_results`(`id`) ON DELETE CASCADE
) COMMENT = 'MySQL - 분석 결과 차트 데이터 (도넛차트, 트리맵 등)';

-- 14. 관련 동영상 (MySQL)
CREATE TABLE `related_videos` (
`id` BIGINT NOT NULL AUTO_INCREMENT,
`analysis_result_id` VARCHAR(36) NOT NULL COMMENT '분석 결과 ID (FK)',
`video_id` VARCHAR(50) NOT NULL COMMENT 'YouTube 동영상 ID',
`title` VARCHAR(500) NOT NULL COMMENT '동영상 제목',
`url` VARCHAR(2000) NOT NULL COMMENT '동영상 URL',
`thumbnail_url` VARCHAR(2000) NULL COMMENT '썸네일 URL',
`channel_name` VARCHAR(255) NULL COMMENT '채널명',
`channel_id` VARCHAR(50) NULL COMMENT '채널 ID',
`published_at` DATETIME NULL COMMENT '동영상 게시 일시',
`duration` VARCHAR(20) NULL COMMENT '동영상 길이 (ISO 8601)',
`view_count` BIGINT NULL COMMENT '조회수',
`relevance_score` DECIMAL(4, 3) NOT NULL COMMENT '관련도 점수',
`keywords` JSON NULL COMMENT '매칭된 키워드들',
`created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
PRIMARY KEY (`id`),
FOREIGN KEY (`analysis_result_id`) REFERENCES `analysis_results`(`id`) ON DELETE CASCADE
) COMMENT = 'MySQL - YouTube API 기반 관련 동영상 (1:N 관계)';

-- ============================================================================
-- 📊 USER ACTIVITY (사용자 활동) - MySQL
-- ============================================================================

-- 15. 분석 히스토리 (MySQL)
CREATE TABLE `analysis_history` (
`id` BIGINT NOT NULL AUTO_INCREMENT,
`news_id` VARCHAR(36) NOT NULL COMMENT '뉴스 기사 ID (FK)',
`user_id` BIGINT NOT NULL COMMENT '사용자 ID (FK)',
`analysis_result_id` VARCHAR(36) NOT NULL COMMENT '분석 결과 ID (FK)',
`version` INT NOT NULL DEFAULT 1 COMMENT '분석 버전',
`analysis_date` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '분석 수행 일시',
`bias_score` DECIMAL(4, 3) NULL COMMENT '편향 점수 (비정규화, 빠른 조회용)',
`bias_type` ENUM('progressive', 'neutral', 'conservative') NULL COMMENT '편향 유형 (비정규화)',
`summary_preview` VARCHAR(200) NULL COMMENT '요약 미리보기',
`is_latest` TINYINT NOT NULL DEFAULT 1 COMMENT '최신 분석 여부',
`created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
PRIMARY KEY (`id`),
FOREIGN KEY (`news_id`) REFERENCES `news`(`id`),
FOREIGN KEY (`user_id`) REFERENCES `users`(`id`),
FOREIGN KEY (`analysis_result_id`) REFERENCES `analysis_results`(`id`)
) COMMENT = 'MySQL - 사용자별 분석 히스토리';

-- 16. 사용자 활동 로그 (MySQL)
CREATE TABLE `user_activities` (
`id` BIGINT NOT NULL AUTO_INCREMENT,
`user_id` BIGINT NOT NULL COMMENT '사용자 ID (FK)',
`activity_type` ENUM('analysis','search','view','share') NOT NULL COMMENT '활동 유형',
`analysis_id` VARCHAR(36) NULL COMMENT '관련 분석 결과 ID (FK)',
`news_id` VARCHAR(36) NULL COMMENT '관련 뉴스 ID (FK)',
`search_query` VARCHAR(500) NULL COMMENT '검색어 (검색 활동시)',
`search_results_count` INT NULL COMMENT '검색 결과 개수',
`metadata` JSON NULL COMMENT '추가 메타데이터',
`ip_address` VARCHAR(45) NULL COMMENT '사용자 IP 주소',
`user_agent` TEXT NULL COMMENT '사용자 브라우저 정보',
`created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
`deleted_at` DATETIME NULL,
PRIMARY KEY (`id`),
FOREIGN KEY (`user_id`) REFERENCES `users`(`id`),
FOREIGN KEY (`analysis_id`) REFERENCES `analysis_results`(`id`),
FOREIGN KEY (`news_id`) REFERENCES `news`(`id`)
) COMMENT = 'MySQL - 사용자 활동 로그 (분석, 검색, 조회, 공유)';

-- 17. 사용자 검색 히스토리 (MySQL)
CREATE TABLE `user_search_history` (
`id` BIGINT NOT NULL AUTO_INCREMENT,
`user_id` BIGINT NOT NULL COMMENT '사용자 ID (FK)',
`search_query` VARCHAR(500) NOT NULL COMMENT '검색어',
`search_type` ENUM('news','analysis','mixed') NOT NULL COMMENT '검색 유형',
`results_count` INT NOT NULL DEFAULT 0 COMMENT '검색 결과 개수',
`clicked_result_id` VARCHAR(36) NULL COMMENT '클릭한 결과 ID',
`clicked_result_type` ENUM('news', 'analysis') NULL COMMENT '클릭한 결과 유형',
`search_category` VARCHAR(50) NULL COMMENT '검색 카테고리 필터',
`search_filters` JSON NULL COMMENT '적용된 검색 필터들',
`response_time_ms` INT NULL COMMENT '검색 응답 시간 (성능 모니터링)',
`searched_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
`deleted_at` DATETIME NULL,
PRIMARY KEY (`id`),
FOREIGN KEY (`user_id`) REFERENCES `users`(`id`)
) COMMENT = 'MySQL - 사용자 검색 히스토리';

-- ============================================================================
-- ⚡ CACHE & PERFORMANCE (캐시 및 성능) - MySQL + Redis
-- ============================================================================

-- 18. 실시간 캐시 (MySQL 백업용, 실제는 Redis 사용)
CREATE TABLE `realtime_cache` (
`id` BIGINT NOT NULL AUTO_INCREMENT,
`cache_type` ENUM('realtime','trending','popular','category') NOT NULL COMMENT '캐시 유형',
`category_id` BIGINT NULL COMMENT '카테고리별 캐시인 경우',
`news_list` JSON NOT NULL COMMENT '뉴스 목록 JSON 데이터',
`statistics` JSON NULL COMMENT '통계 정보 (조회수, 트렌드 등)',
`updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '캐시 갱신 시간',
`expires_at` DATETIME NOT NULL COMMENT '캐시 만료 시간 (TTL)',
PRIMARY KEY (`id`),
FOREIGN KEY (`category_id`) REFERENCES `categories`(`id`)
) COMMENT = 'MySQL - 실시간 캐시 백업 (주 캐시는 Redis)';

-- 19. 사용자 통계 캐시 (MySQL)
CREATE TABLE `user_statistics_cache` (
`id` BIGINT NOT NULL AUTO_INCREMENT,
`user_id` BIGINT NOT NULL COMMENT '사용자 ID (FK)',
`total_analyses` BIGINT NOT NULL DEFAULT 0 COMMENT '총 분석 횟수',
`last_analysis_date` DATETIME NULL COMMENT '마지막 분석 일자',
`bias_statistics` JSON NOT NULL COMMENT '편향성 통계 (도넛차트용)',
`category_preferences` JSON NOT NULL COMMENT '선호 카테고리 통계',
`media_outlet_preferences` JSON NOT NULL COMMENT '선호 언론사 통계',
`recent_activities` JSON NULL COMMENT '최근 활동 10개',
`cache_updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '1시간마다 갱신',
`deleted_at` DATETIME NULL,
PRIMARY KEY (`id`),
FOREIGN KEY (`user_id`) REFERENCES `users`(`id`),
UNIQUE KEY `uk_user_id` (`user_id`)
) COMMENT = 'MySQL - 사용자 대시보드 통계 캐시 (성능 최적화)';

-- ============================================================================
-- 🔍 SEARCH & ANALYTICS (검색 및 분석) - MySQL + OpenSearch
-- ============================================================================

-- 20. 사용자 분석 히스토리 (MySQL)
CREATE TABLE `user_analysis_history` (
`id` BIGINT NOT NULL AUTO_INCREMENT,
`user_id` BIGINT NOT NULL COMMENT '사용자 ID (FK)',
`session_id` VARCHAR(64) NOT NULL COMMENT '분석 세션 ID (FK)',
`news_title` VARCHAR(500) NOT NULL COMMENT '뉴스 제목 (비정규화)',
`bias_type` ENUM('progressive', 'neutral', 'conservative') NULL COMMENT '편향 유형',
`bias_score` DECIMAL(4, 3) NULL COMMENT '편향 점수',
`summary_preview` VARCHAR(200) NULL COMMENT '요약 미리보기',
`category` ENUM('POLITICS', 'ECONOMY', 'SOCIETY', 'CULTURE') NOT NULL COMMENT '카테고리',
`media_outlet_id` BIGINT NOT NULL COMMENT '언론사 ID (FK)',
`analyzed_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '분석 일시',
PRIMARY KEY (`id`),
FOREIGN KEY (`user_id`) REFERENCES `users`(`id`),
FOREIGN KEY (`session_id`) REFERENCES `analysis_sessions`(`session_id`),
FOREIGN KEY (`media_outlet_id`) REFERENCES `media_outlets`(`id`)
) COMMENT = 'MySQL - 사용자 분석 히스토리 (대시보드용)';

-- 21. 사용자 분석 검색 히스토리 (MySQL)
CREATE TABLE `user_analysis_search_history` (
`id` BIGINT NOT NULL AUTO_INCREMENT,
`user_id` BIGINT NOT NULL COMMENT '사용자 ID (FK)',
`search_query` VARCHAR(500) NOT NULL COMMENT '검색어 (내 분석 검색)',
`search_engine` ENUM('elasticsearch', 'opensearch') NOT NULL DEFAULT 'opensearch' COMMENT '사용한 검색엔진',
`results_count` INT NOT NULL DEFAULT 0 COMMENT '검색 결과 개수',
`clicked_analysis_id` VARCHAR(36) NULL COMMENT '클릭한 분석 결과 ID',
`search_filters` JSON NULL COMMENT '적용된 필터 (카테고리, 편향성, 날짜)',
`elasticsearch_query` JSON NULL COMMENT '실제 검색 쿼리 (디버깅용)',
`response_time_ms` INT NULL COMMENT '검색 응답 시간',
`total_hits` BIGINT NULL COMMENT '총 검색 결과 수',
`searched_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
`deleted_at` DATETIME NULL,
PRIMARY KEY (`id`),
FOREIGN KEY (`user_id`) REFERENCES `users`(`id`)
) COMMENT = 'MySQL - 내 분석 검색 히스토리 (OpenSearch 연동)';

-- 22. 분석 검색 인덱스 메타데이터 (MySQL)
CREATE TABLE `analysis_search_index_metadata` (
`id` BIGINT NOT NULL AUTO_INCREMENT,
`analysis_result_id` VARCHAR(36) NOT NULL COMMENT '분석 결과 ID (FK)',
`user_id` BIGINT NOT NULL COMMENT '분석 소유자 ID (FK)',
`elasticsearch_index` VARCHAR(100) NOT NULL COMMENT 'OpenSearch 인덱스명',
`document_id` VARCHAR(100) NOT NULL COMMENT 'OpenSearch 문서 ID',
`indexed_fields` JSON NOT NULL COMMENT '인덱싱된 필드 정보',
`search_boost_score` DECIMAL(4, 2) NULL DEFAULT 1.00 COMMENT '검색 부스트 점수',
`index_status` ENUM('pending', 'indexed', 'failed', 'deleted') NULL DEFAULT 'pending' COMMENT '인덱싱 상태',
`last_indexed_at` DATETIME NULL COMMENT '마지막 인덱싱 시간',
`index_version` INT NULL DEFAULT 1 COMMENT '인덱스 버전',
`error_message` TEXT NULL COMMENT '인덱싱 실패시 에러 메시지',
`created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
`updated_at` DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,
`deleted_at` DATETIME NULL,
PRIMARY KEY (`id`),
FOREIGN KEY (`analysis_result_id`) REFERENCES `analysis_results`(`id`),
FOREIGN KEY (`user_id`) REFERENCES `users`(`id`)
) COMMENT = 'MySQL - OpenSearch 인덱스 메타데이터 관리';

-- 23. 인기 검색어 (MySQL)
CREATE TABLE `popular_search_terms` (
`id` BIGINT NOT NULL AUTO_INCREMENT,
`search_term` VARCHAR(255) NOT NULL COMMENT '검색어',
`search_count` BIGINT NOT NULL DEFAULT 1 COMMENT '검색 횟수 누적',
`category_id` BIGINT NULL COMMENT '카테고리 ID (FK, NULL이면 전체)',
`trend_score` DECIMAL(8, 3) NOT NULL DEFAULT 0.000 COMMENT '트렌드 점수 (최근 검색 빈도)',
`last_searched_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '마지막 검색 시간',
`created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
`updated_at` DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,
PRIMARY KEY (`id`),
FOREIGN KEY (`category_id`) REFERENCES `categories`(`id`)
) COMMENT = 'MySQL - 인기 검색어 통계 (실시간 트렌드)';

-- ============================================================================
-- 📊 기본 데이터 삽입
-- ============================================================================

INSERT INTO `categories` (`name`, `name_en`, `description`, `display_order`) VALUES
('정치', 'politics', '정치 관련 뉴스', 1),
('경제', 'economy', '경제 관련 뉴스', 2),
('사회', 'society', '사회 관련 뉴스', 3),
('문화', 'culture', '문화 관련 뉴스', 4);

-- ============================================================================
-- 🎯 DATABASE 사용 계획 요약
-- ============================================================================
/*
1. MySQL 8.0+ (메인 데이터베이스)
    - 모든 구조화된 데이터 저장
    - 사용자, 뉴스, 분석 결과, 활동 로그
    - ACID 트랜잭션 보장 필요한 모든 데이터

2. Redis 7.0+ (인메모리 캐시)
    - 세션 관리 (JWT 토큰, 로그인 세션)
    - 실시간 캐시 (급상승 뉴스, 실시간 뉴스)
    - WebSocket 연결 관리
    - TTL 기반 자동 만료

3. OpenSearch (검색 엔진)
    - 뉴스 전문 검색
    - 사용자 분석 결과 검색
    - 자동완성, 필터링, 정렬
    - 실시간 인덱싱

4. AWS S3 (파일 저장소)
    - 프로필 이미지
    - 뉴스 썸네일
    - 차트 이미지 캐시
      */