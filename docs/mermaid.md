# 🗺️ Factory BE ERD 다이어그램 (Mermaid 코드)

## 📊 **전체 ERD (간략 버전)**

```mermaid
erDiagram
    %% 사용자 관리
    users ||--o{ social_account : "has"
    users ||--o{ sessions : "has"
    
    %% 뉴스 컨텐츠
    categories ||--o{ news : "categorizes"
    media_outlets ||--o{ journalists : "employs"
    media_outlets ||--o{ news : "publishes"
    journalists ||--o{ news : "writes"
    
    %% AI 분석
    users ||--o{ analysis_sessions : "requests"
    news ||--o{ analysis_sessions : "analyzed_in"
    analysis_sessions ||--|| analysis_results : "produces"
    analysis_results ||--|| bias_analysis_details : "contains"
    analysis_results ||--o{ keyword_analysis_details : "contains"
    analysis_results ||--|| fact_check_results : "contains"
    analysis_results ||--o{ analysis_charts : "contains"
    analysis_results ||--o{ related_videos : "contains"
    
    %% 사용자 활동
    users ||--o{ analysis_history : "has"
    users ||--o{ user_activities : "performs"
    users ||--o{ user_search_history : "searches"
    
    %% 캐시 및 성능
    users ||--|| user_statistics_cache : "cached_stats"
    categories ||--o{ realtime_cache : "cached_in"
    
    %% 검색
    users ||--o{ user_analysis_search_history : "searches_analysis"
    analysis_results ||--|| analysis_search_index_metadata : "indexed_as"
    categories ||--o{ popular_search_terms : "searched_in"

    %% 주요 엔티티 정의
    users {
        bigint id PK
        varchar name
        varchar email UK
        enum user_role
        datetime created_at
        datetime deleted_at
    }
    
    news {
        varchar id PK
        bigint media_outlet_id FK
        bigint journalist_id FK
        varchar title
        longtext content
        varchar url UK
        enum category
        datetime published_at
        datetime created_at
    }
    
    analysis_results {
        varchar id PK
        varchar news_id FK
        varchar session_id FK
        bigint user_id FK
        text summary
        text bias_description
        boolean is_public
        datetime created_at
    }
```

---

## 👤 **사용자 관리 ERD**

```mermaid
erDiagram
    users ||--o{ social_account : "has multiple accounts"
    users ||--o{ sessions : "has active sessions"
    
    users {
        bigint id PK "AUTO_INCREMENT"
        varchar name "NOT NULL"
        varchar email "UNIQUE NOT NULL"
        text profile_image "NULL"
        enum user_role "ADMIN, USER"
        datetime created_at "DEFAULT CURRENT_TIMESTAMP"
        datetime updated_at "ON UPDATE CURRENT_TIMESTAMP"
        datetime deleted_at "Soft Delete"
    }
    
    social_account {
        bigint id PK "AUTO_INCREMENT"
        bigint user_id FK "NOT NULL"
        enum provider "KAKAO, GOOGLE"
        varchar social_id "NOT NULL"
        varchar email "NOT NULL"
        varchar social_username "NULL"
        datetime created_at "DEFAULT CURRENT_TIMESTAMP"
        datetime updated_at "ON UPDATE CURRENT_TIMESTAMP"
    }
    
    sessions {
        varchar session_id PK "64 chars"
        varchar user_id "Redis compatible"
        varchar ip_address "IPv4/IPv6"
        text user_agent "Browser info"
        datetime created_at "DEFAULT CURRENT_TIMESTAMP"
        datetime last_access_time "DEFAULT CURRENT_TIMESTAMP"
        datetime expires_at "NOT NULL"
        tinyint is_active "DEFAULT 1"
    }
```

---

## 📰 **뉴스 컨텐츠 ERD**

```mermaid
erDiagram
    categories ||--o{ news : "categorizes"
    media_outlets ||--o{ journalists : "employs"
    media_outlets ||--o{ news : "publishes"
    journalists ||--o{ news : "writes"
    
    categories {
        bigint id PK "AUTO_INCREMENT"
        varchar name "정치, 경제, 사회, 문화"
        varchar name_en "politics, economy, society, culture"
        varchar description "카테고리 설명"
        int display_order "화면 표시 순서"
        boolean is_active "DEFAULT TRUE"
        datetime created_at "DEFAULT CURRENT_TIMESTAMP"
    }
    
    media_outlets {
        bigint id PK "AUTO_INCREMENT"
        varchar name "언론사명 (크롤링)"
        varchar domain "도메인 (UNIQUE)"
        varchar website "웹사이트 URL"
        enum political_bias "progressive, neutral, conservative"
        enum crawling_platform "rss, sitemap, api, manual, scraping"
        varchar crawling_url "실제 크롤링 URL"
        json crawling_settings "크롤링 설정"
        boolean is_active "DEFAULT TRUE"
        datetime created_at "DEFAULT CURRENT_TIMESTAMP"
        datetime updated_at "ON UPDATE CURRENT_TIMESTAMP"
        datetime deleted_at "Soft Delete"
    }
    
    journalists {
        bigint id PK "AUTO_INCREMENT"
        varchar name "기자명 (크롤링)"
        bigint media_outlet_id FK "NOT NULL"
        boolean is_active "DEFAULT TRUE"
        datetime created_at "DEFAULT CURRENT_TIMESTAMP"
        datetime updated_at "ON UPDATE CURRENT_TIMESTAMP"
        datetime deleted_at "Soft Delete"
    }
    
    news {
        varchar id PK "UUID 36 chars"
        bigint media_outlet_id FK "NOT NULL"
        bigint journalist_id FK "NULL if no match"
        varchar title "기사 제목 (크롤링)"
        longtext content "기사 본문 (크롤링)"
        varchar url "기사 URL (UNIQUE)"
        varchar author_name "작성자명 (비정규화)"
        datetime published_at "게시 일시"
        datetime crawled_at "크롤링 일시"
        enum category "POLITICS, ECONOMY, SOCIETY, CULTURE"
        text summary "AI 생성 요약"
        json keywords "AI 추출 키워드"
        bigint view_count "DEFAULT 0"
        enum crawling_source "크롤링 방식"
        varchar crawling_platform "크롤링 플랫폼"
        enum status "draft, published, archived, deleted"
        datetime created_at "DEFAULT CURRENT_TIMESTAMP"
        datetime updated_at "ON UPDATE CURRENT_TIMESTAMP"
        datetime deleted_at "Soft Delete"
    }
```

---

## 🤖 **AI 분석 ERD**

```mermaid
erDiagram
    users ||--o{ analysis_sessions : "requests analysis"
    news ||--o{ analysis_sessions : "gets analyzed"
    analysis_sessions ||--|| analysis_results : "produces result"
    analysis_results ||--|| bias_analysis_details : "has bias details"
    analysis_results ||--o{ keyword_analysis_details : "has keywords"
    analysis_results ||--|| fact_check_results : "has fact check"
    analysis_results ||--o{ analysis_charts : "has charts"
    analysis_results ||--o{ related_videos : "has videos"
    
    analysis_sessions {
        varchar session_id PK "UUID 64 chars"
        bigint user_id FK "분석 요청자"
        varchar news_id FK "크롤링 완료 후 생성"
        varchar original_url "사용자 입력 URL"
        enum status "queued, processing, completed, failed, cancelled"
        varchar current_step "현재 진행 단계"
        int progress_percentage "0-100 진행률"
        text error_message "오류 메시지"
        json progress_logs "단계별 로그"
        datetime estimated_completion_time "예상 완료 시간"
        int queue_position "대기열 순서"
        datetime created_at "DEFAULT CURRENT_TIMESTAMP"
        datetime completed_at "분석 완료 시간"
        datetime deleted_at "세션 정리 시간"
    }
    
    analysis_results {
        varchar id PK "UUID 36 chars"
        varchar news_id FK "분석 대상 뉴스"
        varchar session_id FK "분석 세션"
        bigint user_id FK "분석 요청자"
        text summary "AI 생성 요약"
        text fact_description "사실 내용"
        varchar bias_direction "편향 방향"
        varchar bias_intensity "편향 강도"
        text bias_description "편향 설명"
        text detailed_bias_description "상세 편향 분석"
        text political_perspective "정치적 시각"
        boolean is_public "공개 여부"
        int version "분석 버전"
        datetime created_at "DEFAULT CURRENT_TIMESTAMP"
        datetime updated_at "ON UPDATE CURRENT_TIMESTAMP"
        datetime deleted_at "Soft Delete"
    }
    
    bias_analysis_details {
        bigint id PK "AUTO_INCREMENT"
        varchar analysis_result_id FK "분석 결과 ID"
        enum overall_bias "progressive, neutral, conservative"
        decimal overall_score "편향 점수 (-1.000 ~ 1.000)"
        decimal confidence "AI 신뢰도 (0.000 ~ 1.000)"
        decimal fact_percentage "사실 비율 (%)"
        decimal opinion_percentage "의견 비율 (%)"
        decimal positive_sentiment "긍정 감정 (%)"
        decimal neutral_sentiment "중립 감정 (%)"
        decimal negative_sentiment "부정 감정 (%)"
        datetime created_at "DEFAULT CURRENT_TIMESTAMP"
        datetime deleted_at "Soft Delete"
    }
    
    keyword_analysis_details {
        bigint id PK "AUTO_INCREMENT"
        varchar analysis_result_id FK "분석 결과 ID"
        varchar keyword "추출된 키워드"
        int frequency "출현 빈도"
        decimal importance "중요도 (TF-IDF)"
        int rank "중요도 순위"
        json sentences "포함된 문장들"
        datetime created_at "DEFAULT CURRENT_TIMESTAMP"
    }
    
    fact_check_results {
        bigint id PK "AUTO_INCREMENT"
        varchar analysis_result_id FK "분석 결과 ID"
        enum status "verified, unverified, disputed, error"
        text message "팩트체크 결과"
        decimal confidence "신뢰도"
        int search_result_count "구글 검색 결과 수"
        json sources "팩트체크 소스"
        json google_factcheck_data "구글 API 응답"
        json search_keywords "검색 키워드"
        datetime created_at "DEFAULT CURRENT_TIMESTAMP"
        datetime deleted_at "Soft Delete"
    }
    
    analysis_charts {
        bigint id PK "AUTO_INCREMENT"
        varchar analysis_result_id FK "분석 결과 ID"
        enum chart_type "sentiment_donut, keyword_treemap, network_chart, bias_scatter"
        json chart_data "Highcharts 데이터"
        datetime created_at "DEFAULT CURRENT_TIMESTAMP"
    }
    
    related_videos {
        bigint id PK "AUTO_INCREMENT"
        varchar analysis_result_id FK "분석 결과 ID"
        varchar video_id "YouTube 동영상 ID"
        varchar title "동영상 제목"
        varchar url "동영상 URL"
        varchar thumbnail_url "썸네일 URL"
        varchar channel_name "채널명"
        varchar channel_id "채널 ID"
        datetime published_at "게시 일시"
        varchar duration "길이 (ISO 8601)"
        bigint view_count "조회수"
        decimal relevance_score "관련도 점수"
        json keywords "매칭 키워드"
        datetime created_at "DEFAULT CURRENT_TIMESTAMP"
    }
```

---

## 📊 **사용자 활동 ERD**

```mermaid
erDiagram
    users ||--o{ analysis_history : "has analysis history"
    users ||--o{ user_activities : "performs activities"
    users ||--o{ user_search_history : "has search history"
    news ||--o{ analysis_history : "appears in history"
    analysis_results ||--o{ analysis_history : "recorded in history"
    analysis_results ||--o{ user_activities : "activity target"
    news ||--o{ user_activities : "activity target"
    
    analysis_history {
        bigint id PK "AUTO_INCREMENT"
        varchar news_id FK "뉴스 ID"
        bigint user_id FK "사용자 ID"
        varchar analysis_result_id FK "분석 결과 ID"
        int version "분석 버전"
        datetime analysis_date "분석 일시"
        decimal bias_score "편향 점수 (비정규화)"
        enum bias_type "편향 유형 (비정규화)"
        varchar summary_preview "요약 미리보기"
        tinyint is_latest "최신 분석 여부"
        datetime created_at "DEFAULT CURRENT_TIMESTAMP"
    }
    
    user_activities {
        bigint id PK "AUTO_INCREMENT"
        bigint user_id FK "사용자 ID"
        enum activity_type "analysis, search, view, share"
        varchar analysis_id FK "관련 분석 ID"
        varchar news_id FK "관련 뉴스 ID"
        varchar search_query "검색어"
        int search_results_count "검색 결과 수"
        json metadata "추가 메타데이터"
        varchar ip_address "사용자 IP"
        text user_agent "브라우저 정보"
        datetime created_at "DEFAULT CURRENT_TIMESTAMP"
        datetime deleted_at "Soft Delete"
    }
    
    user_search_history {
        bigint id PK "AUTO_INCREMENT"
        bigint user_id FK "사용자 ID"
        varchar search_query "검색어"
        enum search_type "news, analysis, mixed"
        int results_count "검색 결과 수"
        varchar clicked_result_id "클릭한 결과 ID"
        enum clicked_result_type "news, analysis"
        varchar search_category "검색 카테고리"
        json search_filters "검색 필터"
        int response_time_ms "응답 시간"
        datetime searched_at "검색 일시"
        datetime deleted_at "Soft Delete"
    }
```

---

## ⚡ **캐시 및 검색 ERD**

```mermaid
erDiagram
    categories ||--o{ realtime_cache : "cached by category"
    users ||--|| user_statistics_cache : "has cached stats"
    users ||--o{ user_analysis_search_history : "searches own analysis"
    analysis_results ||--|| analysis_search_index_metadata : "indexed for search"
    categories ||--o{ popular_search_terms : "searched within category"
    users ||--o{ user_analysis_history : "has analysis summary"
    
    realtime_cache {
        bigint id PK "AUTO_INCREMENT"
        enum cache_type "realtime, trending, popular, category"
        bigint category_id FK "카테고리별 캐시"
        json news_list "뉴스 목록 JSON"
        json statistics "통계 정보"
        datetime updated_at "캐시 갱신 시간"
        datetime expires_at "캐시 만료 시간"
    }
    
    user_statistics_cache {
        bigint id PK "AUTO_INCREMENT"
        bigint user_id FK "사용자 ID (UNIQUE)"
        bigint total_analyses "총 분석 횟수"
        datetime last_analysis_date "마지막 분석 일자"
        json bias_statistics "편향성 통계"
        json category_preferences "선호 카테고리"
        json media_outlet_preferences "선호 언론사"
        json recent_activities "최근 활동 10개"
        datetime cache_updated_at "1시간마다 갱신"
        datetime deleted_at "Soft Delete"
    }
    
    user_analysis_search_history {
        bigint id PK "AUTO_INCREMENT"
        bigint user_id FK "사용자 ID"
        varchar search_query "검색어 (내 분석)"
        enum search_engine "elasticsearch, opensearch"
        int results_count "검색 결과 수"
        varchar clicked_analysis_id "클릭한 분석 ID"
        json search_filters "검색 필터"
        json elasticsearch_query "실제 쿼리 (디버깅)"
        int response_time_ms "응답 시간"
        bigint total_hits "총 검색 결과"
        datetime searched_at "검색 일시"
        datetime deleted_at "Soft Delete"
    }
    
    analysis_search_index_metadata {
        bigint id PK "AUTO_INCREMENT"
        varchar analysis_result_id FK "분석 결과 ID"
        bigint user_id FK "분석 소유자 ID"
        varchar elasticsearch_index "OpenSearch 인덱스명"
        varchar document_id "OpenSearch 문서 ID"
        json indexed_fields "인덱싱 필드 정보"
        decimal search_boost_score "검색 부스트 점수"
        enum index_status "pending, indexed, failed, deleted"
        datetime last_indexed_at "마지막 인덱싱 시간"
        int index_version "인덱스 버전"
        text error_message "인덱싱 에러 메시지"
        datetime created_at "DEFAULT CURRENT_TIMESTAMP"
        datetime updated_at "ON UPDATE CURRENT_TIMESTAMP"
        datetime deleted_at "Soft Delete"
    }
    
    popular_search_terms {
        bigint id PK "AUTO_INCREMENT"
        varchar search_term "검색어"
        bigint search_count "검색 횟수 누적"
        bigint category_id FK "카테고리 ID (NULL이면 전체)"
        decimal trend_score "트렌드 점수"
        datetime last_searched_at "마지막 검색 시간"
        datetime created_at "DEFAULT CURRENT_TIMESTAMP"
        datetime updated_at "ON UPDATE CURRENT_TIMESTAMP"
    }
    
    user_analysis_history {
        bigint id PK "AUTO_INCREMENT"
        bigint user_id FK "사용자 ID"
        varchar session_id FK "분석 세션 ID"
        varchar news_title "뉴스 제목 (비정규화)"
        enum bias_type "편향 유형"
        decimal bias_score "편향 점수"
        varchar summary_preview "요약 미리보기"
        enum category "POLITICS, ECONOMY, SOCIETY, CULTURE"
        bigint media_outlet_id FK "언론사 ID"
        datetime analyzed_at "분석 일시"
    }
```

---

## 🔄 **데이터 플로우 다이어그램**

```mermaid
flowchart TD
    A[사용자 로그인] --> B[소셜 계정 연동]
    B --> C[세션 생성]
    C --> D[뉴스 URL 입력]
    D --> E[분석 세션 생성]
    E --> F[URL 헬스체크]
    F --> G[뉴스 크롤링]
    G --> H[언론사/기자 매칭]
    H --> I[카테고리 자동 분류]
    I --> J[뉴스 DB 저장]
    J --> K[AI 편향성 분석]
    K --> L[키워드 추출]
    L --> M[팩트체크 수행]
    M --> N[차트 데이터 생성]
    N --> O[관련 영상 검색]
    O --> P[분석 결과 저장]
    P --> Q[검색 인덱싱]
    Q --> R[사용자 히스토리 업데이트]
    R --> S[통계 캐시 갱신]
    
    %% 병렬 처리
    E --> T[WebSocket 연결]
    T --> U[실시간 진행상황 전송]
    
    %% 캐시 시스템
    J --> V[실시간 뉴스 캐시]
    P --> W[분석 결과 캐시]
    
    %% 검색 시스템
    Q --> X[OpenSearch 인덱싱]
    X --> Y[검색 가능]
    
    style A fill:#e1f5fe
    style P fill:#c8e6c9
    style S fill:#fff3e0
```

---

## 🎯 **주요 관계 요약**

### **1:1 관계**
- `analysis_sessions` ↔ `analysis_results`
- `analysis_results` ↔ `bias_analysis_details`
- `analysis_results` ↔ `fact_check_results`
- `users` ↔ `user_statistics_cache`
- `analysis_results` ↔ `analysis_search_index_metadata`

### **1:N 관계**
- `users` → `social_account`, `sessions`, `analysis_sessions`, `user_activities`
- `media_outlets` → `journalists`, `news`
- `categories` → `news`, `realtime_cache`, `popular_search_terms`
- `analysis_results` → `keyword_analysis_details`, `analysis_charts`, `related_videos`

### **N:M 관계 (중간 테이블로 해결)**
- 사용자 ↔ 뉴스 (analysis_history)
- 사용자 ↔ 분석 결과 (user_activities)
- 검색어 ↔ 카테고리 (popular_search_terms)

이 ERD 다이어그램들을 머메이드 라이브 에디터나 지원하는 도구에 복사해서 사용하실 수 있습니다! 🎯