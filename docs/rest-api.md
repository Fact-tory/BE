# 🎯 Factory BE REST API 명세서

> 뉴스 크롤링, AI 분석, 검색 시스템을 위한 RESTful API

## 📋 **Base URL 및 인증**

### **Base URL**
```
Production: https://api.factory.com/api/v1
Development: http://localhost:8080/api/v1
```

### **인증 방식**
```http
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

---

## 🔐 **1. 인증 및 세션 관리 API**

### **1.1 소셜 로그인 URL 생성**
> 📁 [AuthController.java#L25-L35](src/main/java/com/commonground/be/domain/auth/controller/AuthController.java#L25-L35) - `generateSocialLoginUrl` 메서드

```http
GET /api/v1/auth/social/{provider}/url?redirect_uri={uri}&state={csrf_token}
```

**Path Parameters:**
- `provider` (string, required): 소셜 로그인 제공자 - `kakao` | `google`

**Query Parameters:**
- `redirect_uri` (string, required): 로그인 완료 후 리다이렉트 URI
- `state` (string, optional): CSRF 방지 토큰 (자동 생성)

**Response 200 OK:**
```json
{
  "success": true,
  "data": {
    "loginUrl": "https://kauth.kakao.com/oauth/authorize?client_id=...",
    "state": "csrf_token_12345",
    "expiresIn": 600
  }
}
```

### **1.2 소셜 로그인 콜백**
> 📁 [SocialAuthController.java#L42-L58](src/main/java/com/commonground/be/domain/social/controller/SocialAuthController.java#L42-L58) - `socialLoginCallback` 메서드

```http
POST /api/v1/auth/social/{provider}/callback
Content-Type: application/json
```

**Path Parameters:**
- `provider` (string, required): 소셜 로그인 제공자 - `kakao` | `google`

**Request Body:**
```json
{
  "code": "authorization_code_from_provider",
  "state": "csrf_token_12345",
  "redirect_uri": "https://neutralgear.com/auth/callback"
}
```

**Response 200 OK:**
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIs...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIs...",
    "user": {
      "id": 12345,
      "name": "홍길동",
      "email": "user@example.com",
      "provider": "kakao",
      "profileImage": "https://profile.image.url",
      "isFirstLogin": false
    },
    "sessionInfo": {
      "sessionId": "sess_550e8400-e29b-41d4-a716-446655440000",
      "expiresAt": "2025-08-22T10:30:00Z"
    }
  }
}
```

### **1.3 토큰 갱신**
```http
POST /api/v1/auth/refresh
Content-Type: application/json
```

**Request Body:**
```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiIs..."
}
```

**Response 200 OK:**
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIs...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIs...",
    "expiresIn": 900
  }
}
```

### **1.4 현재 사용자 정보**
```http
GET /api/v1/auth/me
Authorization: Bearer {accessToken}
```

### **1.5 로그아웃**
```http
POST /api/v1/auth/logout
Authorization: Bearer {accessToken}
Content-Type: application/json
```

**Request Body:**
```json
{
  "everywhere": false
}
```

### **1.6 회원탈퇴**
```http
DELETE /api/v1/auth/withdraw?reason={reason}&feedback={feedback}
Authorization: Bearer {accessToken}
```

**Query Parameters:**
- `reason` (string, optional): 탈퇴 사유 - `privacy` | `unused` | `alternative` | `other`
- `feedback` (string, optional): 추가 피드백 (500자 이내)

---

## 📊 **2. 메인 페이지 API**

### **2.1 메인 대시보드 통합**
> 📁 [NewsController.java#L65-L80](src/main/java/com/commonground/be/domain/news/controller/NewsController.java#L65-L80) - `getMainDashboard` 메서드

```http
GET /api/v1/dashboard/main?realtime_limit={count}&trending_limit={count}&category_limit={count}&search_limit={count}
```

**Query Parameters:**
- `realtime_limit` (int, optional, default: 20, range: 5-50): 실시간 뉴스 개수
- `trending_limit` (int, optional, default: 10, range: 3-20): 급상승 뉴스 개수
- `category_limit` (int, optional, default: 5, range: 3-10): 카테고리별 뉴스 개수
- `search_limit` (int, optional, default: 10, range: 5-20): 인기 검색어 개수

**Response 200 OK:**
```json
{
  "success": true,
  "data": {
    "realtime": {
      "articles": [
        {
          "id": "507f1f77bcf86cd799439011",
          "title": "정부, 새로운 경제정책 발표",
          "url": "https://news.site.com/article/123",
          "summary": "정부가 오늘 새로운 경제정책을 발표했습니다...",
          "publishedAt": "2025-07-22T08:00:00Z",
          "mediaOutletId": 1,
          "mediaOutletName": "조선일보",
          "categoryId": 1,
          "categoryName": "정치",
          "journalistId": 123,
          "journalistName": "김기자",
          "analysisStatus": "completed",
          "analysisId": "507f1f77bcf86cd799439012",
          "biasType": "conservative",
          "biasScore": 0.6,
          "viewCount": 1500,
          "analysisCount": 25
        }
      ],
      "lastUpdated": "2025-07-22T10:25:00Z"
    },
    "trending": {
      "articles": [
        {
          "rank": 1,
          "title": "긴급 속보: 중요한 정치 뉴스",
          "summary": "오늘 발생한 중요한 정치 이슈에 대한 한줄 요약",
          "url": "https://news.site.com/trending/123",
          "analysisId": "507f1f77bcf86cd799439013",
          "categoryName": "정치",
          "scoreChange": 25.8,
          "mediaOutletName": "연합뉴스",
          "authorName": "박기자",
          "biasType": "neutral",
          "biasScore": 0.1
        }
      ],
      "lastUpdated": "2025-07-22T10:20:00Z"
    },
    "categories": {
      "1": {
        "categoryId": 1,
        "categoryName": "정치",
        "articles": [
          {
            "id": "507f1f77bcf86cd799439014",
            "title": "국회 예산안 심의",
            "summary": "국회에서 내년도 예산안을 심의합니다",
            "analysisStatus": "completed",
            "analysisId": "507f1f77bcf86cd799439015"
          }
        ],
        "totalCount": 45
      }
    },
    "popularSearches": [
      {
        "rank": 1,
        "term": "경제정책",
        "count": 1250,
        "trend": "up",
        "changePercent": 15.2
      }
    ]
  }
}
```

### **2.2 실시간 뉴스**
> 📁 [NewsController.java#L95-L115](src/main/java/com/commonground/be/domain/news/controller/NewsController.java#L95-L115) - `getRealtimeNews` 메서드

```http
GET /api/v1/news/realtime?page={page}&limit={limit}&category={category}&sort={sort}&media_outlet={id}&journalist={id}&bias={bias}&analysis_status={status}
```

**Query Parameters:**
- `page` (int, optional, default: 1, min: 1): 페이지 번호
- `limit` (int, optional, default: 20, range: 5-50): 한 페이지 항목 수
- `category` (int, optional): 카테고리 필터 - `1`(정치) | `2`(경제) | `3`(사회) | `4`(문화)
- `sort` (string, optional, default: "latest"): 정렬 기준 - `latest` | `trending` | `bias_score` | `view_count`
- `media_outlet` (int, optional): 언론사 ID 필터
- `journalist` (int, optional): 기자 ID 필터
- `bias` (string, optional): 편향성 필터 - `progressive` | `conservative` | `neutral`
- `analysis_status` (string, optional, default: "completed"): 분석 상태 필터 - `completed` | `processing` | `pending` | `failed`

### **2.3 급상승 뉴스**
```http
GET /api/v1/news/trending?limit={limit}&period={period}&category={category}
```

**Query Parameters:**
- `limit` (int, optional, default: 10, range: 3-20): 조회할 개수
- `period` (string, optional, default: "24h"): 급상승 기간 - `1h` | `6h` | `24h` | `7d`
- `category` (int, optional): 카테고리 필터 - `1` | `2` | `3` | `4`

### **2.4 카테고리별 뉴스**
```http
GET /api/v1/news/categories/{categoryId}?page={page}&limit={limit}&sort={sort}&media_outlet={id}&journalist={id}&bias={bias}&date_from={date}&date_to={date}
```

**Path Parameters:**
- `categoryId` (int, required): 카테고리 ID - `1`(정치) | `2`(경제) | `3`(사회) | `4`(문화)

**Query Parameters:**
- `page` (int, optional, default: 1, min: 1): 페이지 번호
- `limit` (int, optional, default: 20, range: 5-50): 한 페이지 항목 수
- `sort` (string, optional, default: "latest"): 정렬 기준 - `latest` | `trending` | `bias_score` | `view_count`
- `media_outlet` (int, optional): 언론사 ID 필터
- `journalist` (int, optional): 기자 ID 필터
- `bias` (string, optional): 편향성 필터 - `progressive` | `conservative` | `neutral`
- `date_from` (string, optional): 시작 날짜 (YYYY-MM-DD)
- `date_to` (string, optional): 종료 날짜 (YYYY-MM-DD)

---

## 🔍 **3. 뉴스 분석 API**

### **3.1 뉴스 분석 시작 (URL 입력 → 헬스체크 → 크롤링 → AI 분석 통합)**
> 📁 [NewsServiceImpl.java#L156-L220](src/main/java/com/commonground/be/domain/news/service/NewsServiceImpl.java#L156-L220) - `startAnalysis` 메서드

```http
POST /api/v1/analysis/start
Authorization: Bearer {accessToken}
Content-Type: application/json
```

**Request Body:**
```json
{
  "url": "https://news.site.com/article/123",
  "options": {
    "public": false,
    "priority": "normal",
    "forceReanalysis": false,
    "includeVideos": true,
    "includeFactCheck": true
  }
}
```

**Response 200 OK:**
```json
{
  "success": true,
  "data": {
    "sessionId": "550e8400-e29b-41d4-a716-446655440000",
    "articleId": "507f1f77bcf86cd799439011",
    "status": "queued",
    "queuePosition": 3,
    "estimatedWaitTime": 50,
    "isExistingArticle": false,
    "healthCheck": {
      "isAccessible": true,
      "statusCode": 200,
      "isSupported": true,
      "warnings": []
    },
    "wsConnection": {
      "url": "ws://localhost:8080/api/v1/ws/analysis/550e8400-e29b-41d4-a716-446655440000",
      "connectImmediately": true
    },
    "analysisFlow": [
      {
        "step": "health_check",
        "name": "URL 헬스체크",
        "description": "URL 접근성을 확인하고 있습니다",
        "estimatedDuration": 3
      },
      {
        "step": "crawler",
        "name": "뉴스 크롤링",
        "description": "뉴스 원문을 크롤링하고 있습니다",
        "estimatedDuration": 10
      },
      {
        "step": "journalist_matching",
        "name": "기자 정보 매칭",
        "description": "기자 정보를 확인하고 있습니다",
        "estimatedDuration": 5
      },
      {
        "step": "summarizing",
        "name": "AI 요약 생성",
        "description": "AI가 기사를 요약하고 있습니다",
        "estimatedDuration": 8
      },
      {
        "step": "bias_analyzing",
        "name": "편향성 분석",
        "description": "AI 모델이 편향성을 분석하고 있습니다",
        "estimatedDuration": 15
      },
      {
        "step": "fact_checking",
        "name": "팩트 체크",
        "description": "구글 팩트체크 API로 사실 검증하고 있습니다",
        "estimatedDuration": 5
      },
      {
        "step": "chart_generation",
        "name": "차트 생성",
        "description": "차트와 그래프를 생성하고 있습니다",
        "estimatedDuration": 3
      },
      {
        "step": "indexing",
        "name": "검색 인덱싱",
        "description": "검색 가능하도록 인덱싱하고 있습니다",
        "estimatedDuration": 1
      }
    ]
  }
}
```

### **3.3 분석 결과 조회**
```http
GET /api/v1/analysis/result/{analysisId}?charts_format={format}&videos_limit={count}&include_content={boolean}
Authorization: Bearer {accessToken}
```

**Path Parameters:**
- `analysisId` (string, required): 분석 결과 ID

**Query Parameters:**
- `charts_format` (string, optional, default: "highcharts"): 차트 데이터 형식 - `highcharts` | `d3` | `raw` | `none`
- `videos_limit` (int, optional, default: 5, range: 0-10): 관련 영상 개수
- `include_content` (boolean, optional, default: true): 기사 원문 포함 여부

**Response 200 OK:**
```json
{
  "success": true,
  "data": {
    "article": {
      "id": "507f1f77bcf86cd799439011",
      "title": "정부, 새로운 경제정책 발표",
      "url": "https://news.site.com/article/123",
      "content": "정부가 오늘 새로운 경제정책을 발표했다...",
      "summary": "정부가 오늘 새로운 경제정책을 발표했습니다. 중소기업 지원에 중점을 두고 있으며...",
      "author": "김기자",
      "publishedAt": "2025-07-22T08:00:00Z",
      "crawledAt": "2025-07-22T10:25:00Z",
      "mediaOutletId": 1,
      "mediaOutletName": "조선일보",
      "categoryId": 1,
      "categoryName": "정치",
      "journalistId": 123,
      "journalistName": "김기자",
      "factCheck": {
        "status": "verified",
        "message": "구글 팩트체크 API를 통해 사실 검증이 완료되었습니다",
        "confidence": 0.9,
        "sources": [
          {
            "source": "팩트체크닷넷",
            "url": "https://factcheck.net/article/123",
            "claim": "정부 경제정책 발표",
            "rating": "TRUE",
            "reviewDate": "2025-07-22T08:30:00Z"
          },
          {
            "source": "한국언론진흥재단",
            "url": "https://kpf.or.kr/factcheck/456",
            "claim": "중소기업 지원 확대",
            "rating": "MOSTLY_TRUE",
            "reviewDate": "2025-07-22T09:00:00Z"
          }
        ],
        "googleFactCheck": {
          "totalResults": 2,
          "searchKeywords": ["정부 경제정책", "중소기업 지원"],
          "searchResultCount": 15,
          "lastChecked": "2025-07-22T10:25:00Z"
        }
      }
    },
    "analysis": {
      "title": "정부의 새로운 경제정책 발표에 대한 기사",
      "factDescription": "경제정책 발표",
      "biasDirection": "정부 정책에 대해 긍정적 시각",
      "biasIntensity": "중간 정도로 편향됨",
      "biasDescription": "경제정책 발표라는 사실을 보수적 관점에서 70% 편향되게 보도했습니다",
      "detailedBiasDescription": "[경제정책 발표]를 [보수적 관점으로] [중간 정도] 편향되게 보도했습니다",
      "biasAnalysis": {
        "overall": {
          "bias": "conservative",
          "score": 0.6,
          "confidence": 0.85
        },
        "factOpinionRatio": {
          "fact": 70,
          "opinion": 30
        },
        "sentimentAnalysis": {
          "positive": 45,
          "neutral": 35,
          "negative": 20
        },
        "politicalPerspective": "시장 중심적 관점에서 정책을 평가하는 시각"
      },
      "keywordAnalysis": {
        "coreKeywords": [
          {
            "rank": 1,
            "word": "경제정책",
            "importance": 0.92
          },
          {
            "rank": 2,
            "word": "정부",
            "importance": 0.88
          },
          {
            "rank": 3,
            "word": "중소기업",
            "importance": 0.85
          }
        ],
        "keywordSentences": [
          {
            "keyword": "경제정책",
            "sentences": [
              "정부가 새로운 경제정책을 발표했다",
              "경제정책의 효과가 기대된다"
            ]
          }
        ],
        "topKeywords": [
          {
            "word": "정부",
            "frequency": 15,
            "weight": 0.85
          },
          {
            "word": "경제정책",
            "frequency": 12,
            "weight": 0.92
          }
        ]
      }
    },
    "charts": {
      "sentimentDonut": {
        "chart": { "type": "pie" },
        "title": { "text": "감정 분석 비율" },
        "series": [{
          "name": "감정",
          "data": [
            { "name": "긍정", "y": 45 },
            { "name": "중립", "y": 35 },
            { "name": "부정", "y": 20 }
          ]
        }]
      },
      "keywordTreemap": {
        "chart": { "type": "treemap" },
        "title": { "text": "키워드 빈도" },
        "series": [{
          "name": "키워드",
          "data": [
            { "name": "정부", "value": 15 },
            { "name": "경제정책", "value": 12 }
          ]
        }]
      },
      "networkChart": {
        "nodes": [
          {
            "id": "government",
            "label": "정부",
            "weight": 0.8,
            "category": "entity"
          }
        ],
        "edges": []
      }
    },
    "relatedVideos": [
      {
        "title": "경제정책 분석 영상",
        "url": "https://youtube.com/watch?v=abc123",
        "thumbnail": "https://img.youtube.com/vi/abc123/maxresdefault.jpg",
        "channel": "뉴스 채널",
        "publishedAt": "2025-07-21T15:30:00Z",
        "duration": "PT10M23S",
        "viewCount": 15420,
        "relevanceScore": 0.88,
        "keywords": ["경제정책", "정부", "중소기업"]
      }
    ]
  }
}
```

### **3.2 분석 상태 조회**
```http
GET /api/v1/analysis/status/{sessionId}
Authorization: Bearer {accessToken}
```

### **3.3 분석 취소**
```http
DELETE /api/v1/analysis/cancel/{sessionId}?reason={reason}
Authorization: Bearer {accessToken}
```

---

## 👤 **4. 사용자 분석 대시보드 API**

### **4.1 사용자 분석 대시보드 통합**
```http
GET /api/v1/dashboard/user?period={period}&recent_limit={limit}&include_trends={boolean}
Authorization: Bearer {accessToken}
```

**Query Parameters:**
- `period` (string, optional, default: "30d"): 분석 기간 - `7d` | `30d` | `90d` | `1y` | `all`
- `recent_limit` (int, optional, default: 10, range: 5-50): 최근 분석 개수
- `include_trends` (boolean, optional, default: true): 트렌드 데이터 포함

---

## 🔍 **5. 검색 API (사이드바 - 분석 내용 검색)**

### **5.1 내 분석 검색**
```http
GET /api/v1/search/my-analyses?q={query}&filters={filters}&sort={sort}&page={page}&limit={limit}&include_highlights={boolean}
Authorization: Bearer {accessToken}
```

**Query Parameters:**
- `q` (string, required, min_length: 2): 검색어
- `filters` (string, optional): 필터 조건
- `sort` (string, optional, default: "relevance"): 정렬 기준 - `relevance` | `date_desc` | `date_asc` | `bias_score` | `view_count`
- `page` (int, optional, default: 1, min: 1): 페이지 번호
- `limit` (int, optional, default: 20, range: 5-50): 결과 개수
- `include_highlights` (boolean, optional, default: true): 검색어 하이라이트 포함

---

### **8.2 크롤링 소스 목록**
> 📁 [NaverNewsCrawler.java](src/main/java/com/commonground/be/domain/news/service/NaverNewsCrawler.java)

```http
GET /api/v1/crawling/sources?page={page}&limit={limit}&type={type}&status={status}&search={search}
Authorization: Bearer {adminToken}
```

**Query Parameters:**
- `page` (int, optional, default: 1): 페이지 번호
- `limit` (int, optional, default: 20, range: 10-100): 한 페이지 항목 수
- `type` (string, optional): 소스 타입 필터 - `rss` | `sitemap` | `url_list` | `api`
- `status` (string, optional): 상태 필터 - `active` | `inactive` | `error` | `maintenance`
- `search` (string, optional): 이름 검색

### **8.3 크롤링 소스 상세 조회**
```http
GET /api/v1/crawling/sources/{sourceId}?include_recent_runs={boolean}&runs_limit={limit}
Authorization: Bearer {adminToken}
```

**Query Parameters:**
- `include_recent_runs` (boolean, optional, default: false): 최근 실행 기록 포함
- `runs_limit` (int, optional, default: 10): 실행 기록 개수

### **8.4 크롤링 소스 수정**
```http
PUT /api/v1/crawling/sources/{sourceId}
Authorization: Bearer {adminToken}
Content-Type: application/json
```

**Request Body:**
```json
{
  "name": "업데이트된 소스명",
  "config": {
    "url": "https://updated.url.com/rss",
    "schedule": {
      "type": "daily",
      "time": "08:00"
    },
    "maxItems": 200
  },
  "isActive": false
}
```

### **8.5 크롤링 소스 삭제**
```http
DELETE /api/v1/crawling/sources/{sourceId}
Authorization: Bearer {adminToken}
```

### **8.6 크롤링 소스 실행**
```http
POST /api/v1/crawling/sources/{sourceId}/run
Authorization: Bearer {adminToken}
Content-Type: application/json
```

**Request Body:**
```json
{
  "priority": "high",
  "overrideConfig": {
    "maxItems": 50,
    "timeout": 30000
  }
}
```

### **8.7 크롤링 실행 기록**
```http
GET /api/v1/crawling/runs?page={page}&limit={limit}&source_id={id}&status={status}&date_from={date}&date_to={date}
Authorization: Bearer {adminToken}
```

**Query Parameters:**
- `page` (int, optional, default: 1): 페이지 번호
- `limit` (int, optional, default: 20): 한 페이지 항목 수
- `source_id` (string, optional): 크롤링 소스 ID 필터
- `status` (string, optional): 실행 상태 필터 - `running` | `completed` | `failed` | `cancelled`
- `date_from` (string, optional): 시작 날짜 (YYYY-MM-DD)
- `date_to` (string, optional): 종료 날짜 (YYYY-MM-DD)

**Response 200 OK:**
```json
{
  "success": true,
  "data": {
    "runs": [
      {
        "id": "run_456",
        "sourceId": "crawl_source_123",
        "sourceName": "조선일보 RSS 피드",
        "status": "completed",
        "startedAt": "2025-07-22T10:00:00Z",
        "completedAt": "2025-07-22T10:05:30Z",
        "duration": 330,
        "stats": {
          "urlsDiscovered": 45,
          "urlsCrawled": 43,
          "articlesExtracted": 41,
          "articlesProcessed": 40,
          "errors": 2
        },
        "errorLog": [
          {
            "url": "https://error.url.com",
            "error": "Timeout after 30s",
            "timestamp": "2025-07-22T10:03:15Z"
          }
        ]
      }
    ],
    "pagination": {
      "page": 1,
      "limit": 20,
      "total": 150
    }
  }
}
```

### **8.8 발견된 URL 목록**
```http
GET /api/v1/crawling/urls?page={page}&limit={limit}&source_id={id}&status={status}&date_from={date}&date_to={date}&search={query}
Authorization: Bearer {adminToken}
```

**Query Parameters:**
- `page` (int, optional, default: 1): 페이지 번호
- `limit` (int, optional, default: 20): 한 페이지 항목 수
- `source_id` (string, optional): 크롤링 소스 ID 필터
- `status` (string, optional): 처리 상태 필터 - `discovered` | `crawled` | `processed` | `failed` | `ignored`
- `date_from` (string, optional): 발견 시작 날짜 (YYYY-MM-DD)
- `date_to` (string, optional): 발견 종료 날짜 (YYYY-MM-DD)
- `search` (string, optional): URL 검색

**Response 200 OK:**
```json
{
  "success": true,
  "data": {
    "urls": [
      {
        "id": "url_789",
        "url": "https://news.site.com/article/123",
        "sourceId": "crawl_source_123",
        "sourceName": "조선일보 RSS 피드",
        "status": "processed",
        "discoveredAt": "2025-07-22T10:02:00Z",
        "crawledAt": "2025-07-22T10:03:00Z",
        "processedAt": "2025-07-22T10:05:00Z",
        "articleId": "507f1f77bcf86cd799439011",
        "mediaOutletId": 1,
        "mediaOutletName": "조선일보",
        "categoryId": 1,
        "categoryName": "정치",
        "title": "정부, 새로운 경제정책 발표",
        "publishedAt": "2025-07-22T08:00:00Z",
        "metadata": {
          "contentType": "text/html",
          "contentLength": 15420,
          "responseTime": 850
        }
      }
    ],
    "pagination": {
      "page": 1,
      "limit": 20,
      "total": 5432
    },
    "stats": {
      "discovered": 5432,
      "crawled": 5380,
      "processed": 5340,
      "failed": 52,
      "ignored": 40
    }
  }
}
```

---

## 🗂️ **9. 카테고리 관리 API (CRUD)**

### **9.1 카테고리 목록**
> 📁 [CategoryEnum.java](src/main/java/com/commonground/be/domain/news/enums/CategoryEnum.java)

```http
GET /api/v1/categories?page={page}&limit={limit}&active={active}&include_stats={boolean}
```

**Query Parameters:**
- `page` (int, optional, default: 1): 페이지 번호
- `limit` (int, optional, default: 20): 한 페이지 항목 수
- `active` (boolean, optional): 활성 상태 필터
- `include_stats` (boolean, optional, default: false): 통계 정보 포함

**Response 200 OK:**
```json
{
  "success": true,
  "data": {
    "categories": [
      {
        "id": 1,
        "name": "정치",
        "nameEn": "politics",
        "description": "정치 관련 뉴스",
        "displayOrder": 1,
        "isActive": true,
        "createdAt": "2025-01-01T00:00:00Z",
        "updatedAt": "2025-01-01T00:00:00Z",
        "statistics": {
          "totalArticles": 1500,
          "totalAnalyses": 750
        }
      }
    ],
    "pagination": {
      "page": 1,
      "limit": 20,
      "total": 4,
      "totalPages": 1
    }
  }
}
```

### **9.2 카테고리 상세 조회**
```http
GET /api/v1/categories/{categoryId}
Authorization: Bearer {adminToken}
```

### **9.3 카테고리 생성**
```http
POST /api/v1/categories
Authorization: Bearer {adminToken}
Content-Type: application/json
```

**Request Body:**
```json
{
  "name": "과학기술",
  "nameEn": "science",
  "description": "과학기술 관련 뉴스",
  "displayOrder": 5,
  "isActive": true
}
```

### **9.4 카테고리 수정**
```http
PUT /api/v1/categories/{categoryId}
Authorization: Bearer {adminToken}
Content-Type: application/json
```

**Request Body:**
```json
{
  "name": "업데이트된 카테고리명",
  "description": "업데이트된 설명",
  "displayOrder": 3,
  "isActive": false
}
```

### **9.5 카테고리 삭제**
```http
DELETE /api/v1/categories/{categoryId}
Authorization: Bearer {adminToken}
```

---

## 🗂️ **10. 언론사 관리 API (CRUD)**

### **10.1 언론사 목록**
> 📁 [MediaOutlet.java](src/main/java/com/commonground/be/domain/media/entity/MediaOutlet.java)

```http
GET /api/v1/media-outlets?page={page}&limit={limit}&active={active}&bias={bias}&include_stats={boolean}&search={search}&sort={sort}
```

**Query Parameters:**
- `page` (int, optional, default: 1): 페이지 번호
- `limit` (int, optional, default: 20, range: 5-100): 한 페이지 항목 수
- `active` (boolean, optional): 활성 상태 필터
- `bias` (string, optional): 편향성 필터 - `progressive` | `conservative` | `neutral`
- `include_stats` (boolean, optional, default: false): 통계 정보 포함
- `search` (string, optional): 언론사명 검색
- `sort` (string, optional, default: "name_asc"): 정렬 기준 - `name_asc` | `bias_score` | `article_count`

### **10.2 언론사 상세 조회**
```http
GET /api/v1/media-outlets/{outletId}?include_journalists={boolean}&include_recent_articles={boolean}
```

### **10.3 언론사 생성**
```http
POST /api/v1/media-outlets
Authorization: Bearer {adminToken}
Content-Type: application/json
```

**Request Body:**
```json
{
  "name": "새로운 언론사",
  "domain": "newmedia.com",
  "website": "https://newmedia.com",
  "politicalBias": "neutral",
  "biasScore": 0.0,
  "credibilityScore": 0.8,
  "description": "새로운 언론사 설명",
  "foundedYear": 2020,
  "isActive": true
}
```

### **10.4 언론사 수정**
```http
PUT /api/v1/media-outlets/{outletId}
Authorization: Bearer {adminToken}
Content-Type: application/json
```

**Request Body:**
```json
{
  "name": "업데이트된 언론사명",
  "politicalBias": "progressive",
  "biasScore": -0.3,
  "credibilityScore": 0.9,
  "isActive": false
}
```

### **10.5 언론사 삭제**
```http
DELETE /api/v1/media-outlets/{outletId}
Authorization: Bearer {adminToken}
```

---

## 🗂️ **11. 기자 관리 API (CRUD)**

### **11.1 기자 목록**
> 📁 [Journalist.java](src/main/java/com/commonground/be/domain/journal/entity/Journalist.java)

```http
GET /api/v1/journalists?page={page}&limit={limit}&media_outlet={id}&active={active}&include_stats={boolean}&search={search}&specialization={field}
```

**Query Parameters:**
- `page` (int, optional, default: 1): 페이지 번호
- `limit` (int, optional, default: 20, range: 5-100): 한 페이지 항목 수
- `media_outlet` (int, optional): 언론사 ID 필터
- `active` (boolean, optional): 활성 상태 필터
- `include_stats` (boolean, optional, default: false): 통계 정보 포함
- `search` (string, optional): 기자명 검색
- `specialization` (string, optional): 전문 분야 필터

### **11.2 기자 상세 조회**
```http
GET /api/v1/journalists/{journalistId}?include_recent_articles={boolean}&article_limit={limit}
```

### **11.3 기자 생성**
```http
POST /api/v1/journalists
Authorization: Bearer {adminToken}
Content-Type: application/json
```

**Request Body:**
```json
{
  "name": "새로운 기자",
  "mediaOutletId": 1,
  "email": "journalist@newmedia.com",
  "bio": "10년 경력의 정치부 기자",
  "specialization": ["정치", "경제정책"],
  "isActive": true
}
```

### **11.4 기자 수정**
```http
PUT /api/v1/journalists/{journalistId}
Authorization: Bearer {adminToken}
Content-Type: application/json
```

**Request Body:**
```json
{
  "name": "업데이트된 기자명",
  "mediaOutletId": 2,
  "bio": "업데이트된 소개",
  "specialization": ["경제", "사회"],
  "isActive": false
}
```

### **11.5 기자 삭제**
```http
DELETE /api/v1/journalists/{journalistId}
Authorization: Bearer {adminToken}
```

---

## 🗂️ **12. 뉴스 기사 관리 API (CRUD)**

### **12.1 뉴스 기사 목록**
> 📁 [News.java](src/main/java/com/commonground/be/domain/news/entity/News.java)

```http
GET /api/v1/articles?page={page}&limit={limit}&category={id}&media_outlet={id}&journalist={id}&status={status}&date_from={date}&date_to={date}&search={query}
Authorization: Bearer {adminToken}
```

**Query Parameters:**
- `page` (int, optional, default: 1): 페이지 번호
- `limit` (int, optional, default: 20, range: 5-100): 한 페이지 항목 수
- `category` (int, optional): 카테고리 ID 필터
- `media_outlet` (int, optional): 언론사 ID 필터
- `journalist` (int, optional): 기자 ID 필터
- `status` (string, optional): 상태 필터 - `draft` | `published` | `archived` | `deleted`
- `date_from` (string, optional): 시작 날짜 (YYYY-MM-DD)
- `date_to` (string, optional): 종료 날짜 (YYYY-MM-DD)
- `search` (string, optional): 제목/내용 검색

### **12.2 뉴스 기사 상세 조회**
```http
GET /api/v1/articles/{articleId}?include_analysis={boolean}
Authorization: Bearer {adminToken}
```

### **12.3 뉴스 기사 생성**
```http
POST /api/v1/articles
Authorization: Bearer {adminToken}
Content-Type: application/json
```

**Request Body:**
```json
{
  "title": "새로운 뉴스 기사 제목",
  "content": "기사 내용 전문...",
  "url": "https://news.site.com/article/new-123",
  "categoryId": 1,
  "mediaOutletId": 1,
  "journalistId": 123,
  "publishedAt": "2025-07-22T08:00:00Z",
  "status": "published"
}
```

### **12.4 뉴스 기사 수정**
```http
PUT /api/v1/articles/{articleId}
Authorization: Bearer {adminToken}
Content-Type: application/json
```

**Request Body:**
```json
{
  "title": "업데이트된 기사 제목",
  "content": "업데이트된 기사 내용...",
  "categoryId": 2,
  "journalistId": 124,
  "status": "archived"
}
```

### **12.5 뉴스 기사 삭제**
```http
DELETE /api/v1/articles/{articleId}
Authorization: Bearer {adminToken}
```

---

## 🗂️ **13. 사용자 관리 API (CRUD)**

### **13.1 사용자 목록**
> 📁 [User.java](src/main/java/com/commonground/be/domain/user/entity/User.java)

```http
GET /api/v1/users?page={page}&limit={limit}&status={status}&provider={provider}&search={query}&sort={sort}
Authorization: Bearer {adminToken}
```

**Query Parameters:**
- `page` (int, optional, default: 1): 페이지 번호
- `limit` (int, optional, default: 20, range: 5-100): 한 페이지 항목 수
- `status` (string, optional): 상태 필터 - `active` | `inactive` | `suspended` | `deleted`
- `provider` (string, optional): 로그인 제공자 필터 - `kakao` | `google`
- `search` (string, optional): 이름/이메일 검색
- `sort` (string, optional, default: "created_desc"): 정렬 기준 - `created_desc` | `name_asc` | `last_active`

### **13.2 사용자 상세 조회**
```http
GET /api/v1/users/{userId}?include_statistics={boolean}&include_recent_activity={boolean}
Authorization: Bearer {adminToken}
```

### **13.3 사용자 수정**
```http
PUT /api/v1/users/{userId}
Authorization: Bearer {adminToken}
Content-Type: application/json
```

**Request Body:**
```json
{
  "name": "업데이트된 사용자명",
  "email": "updated@email.com",
  "status": "suspended",
  "role": "admin"
}
```

### **13.4 사용자 삭제**
```http
DELETE /api/v1/users/{userId}?hard_delete={boolean}
Authorization: Bearer {adminToken}
```

---

## 🌐 **14. WebSocket 연결 (상태진행창)**

### **13.1 분석 진행 상황**
```
ws://localhost:8080/api/v1/ws/analysis/{sessionId}?token={accessToken}
```

**연결 파라미터:**
- `sessionId` (string, required): 분석 세션 ID
- `token` (string, required): 인증 토큰

**진행 상황 메시지:**
```json
{
  "type": "progress_update",
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "articleTitle": "정부, 새로운 경제정책 발표",
  "status": "fact_checking",
  "progress": 75,
  "currentStep": "구글 팩트체크 API로 사실 검증하고 있습니다",
  "stepName": "팩트 체크",
  "queuePosition": 0,
  "estimatedRemaining": 15,
  "data": {
    "stepDetails": "주요 키워드 기반 팩트 체크 진행 중",
    "completedSteps": ["health_check", "crawler", "journalist_matching", "summarizing", "bias_analyzing"],
    "currentStepIndex": 5,
    "totalSteps": 8
  }
}
```

---

## 📋 **14. 응답 공통 형식**

### **성공 응답**
```json
{
  "success": true,
  "data": { /* 실제 데이터 */ },
  "meta": {
    "version": "v6.1",
    "cache": {
      "ttl": 300,
      "source": "redis"
    },
    "performance": {
      "totalTime": 130,
      "queries": 4
    }
  },
  "timestamp": "2025-07-22T10:30:00Z"
}
```

### **에러 응답**
```json
{
  "success": false,
  "error": {
    "code": "INVALID_PARAMETER",
    "message": "잘못된 파라미터입니다",
    "details": {
      "field": "category",
      "value": "5",
      "reason": "유효하지 않은 카테고리 ID",
      "allowedValues": [1, 2, 3, 4]
    }
  },
  "timestamp": "2025-07-22T10:30:00Z",
  "requestId": "req_20250722_143052_abc123"
}
```

---

## 🎯 **최종 API 개수**

| 카테고리 | API 개수 | 주요 기능 |
|----------|----------|-----------|
| **인증/세션** | 6개 | 소셜로그인, 토큰관리 |
| **메인 페이지** | 4개 | 대시보드, 실시간/급상승 뉴스 |
| **뉴스 분석** | 3개 | URL→헬스체크→크롤링→AI분석 통합 |
| **사용자 분석** | 1개 | 통합 대시보드 |
| **검색** | 1개 | 내 분석 검색 |
| **북마크** | 3개 | 북마크 관리 |
| **URL 기반 크롤링** | 8개 | RSS/사이트맵/URL리스트 크롤링 |
| **카테고리 CRUD** | 5개 | 카테고리 관리 |
| **언론사 CRUD** | 5개 | 언론사 관리 |
| **기자 CRUD** | 5개 | 기자 관리 |
| **뉴스 기사 CRUD** | 5개 | 기사 관리 |
| **사용자 CRUD** | 4개 | 사용자 관리 |
| **WebSocket** | 1개 | 실시간 진행상황 |

**총 51개 API 엔드포인트**로 **완벽한 Factory BE 서비스** 구현 🎯

### ✅ **주요 개선사항**

1. **분석 플로우 통합 및 간소화** ✅
    - URL 입력 → 헬스체크 → 크롤링 → AI 분석을 하나의 API로 통합
    - 불필요한 프리뷰 기능 제거, 순수 분석에 집중

2. **구글 팩트체크 API 통합** ✅
    - 분석 결과에 구글 팩트체크 API 결과 포함
    - 팩트체크 소스, 평가, 신뢰도 등 상세 정보 제공
    - 분석 플로우에 팩트체크 단계 추가

3. **모든 Request Body 자세히 명시** ✅
3. **모든 Request Body 자세히 명시** ✅
    - JSON 예시와 함께 상세한 파라미터 설명
    - 실제 사용 가능한 완전한 요청 예시

4. **URL 기반 크롤링 시스템** ✅
    - RSS 피드, 사이트맵, URL 리스트 지원
    - 크롤링 후 자동 분류 (언론사/카테고리/기자)
    - 유연한 스케줄링 및 모니터링

5. **완벽한 데이터 수집 흐름** ✅
   ```
   URL 입력 → 헬스체크 → 크롤링 → AI 분석 → 팩트체크 → 결과
   ```
   ```
   배치 크롤링: URL 발견 → 크롤링 → 자동 분류 → 분석 대기
   ```

6. **확장 가능한 크롤링 구조** ✅
    - 다양한 소스 타입 지원
    - 설정 가능한 크롤링 규칙
    - 실시간 모니터링 및 로그