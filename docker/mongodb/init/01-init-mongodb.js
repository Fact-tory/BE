// =================================
// Factory BE MongoDB 초기화 스크립트
// 사용자 및 컬렉션 초기 설정
// =================================

// 애플리케이션 전용 데이터베이스로 전환
db = db.getSiblingDB('factory');

// 애플리케이션 전용 사용자 생성 및 권한 부여
db.createUser({
  user: 'factory_app',
  pwd: 'factory_app_dev',
  roles: [
    {
      role: 'readWrite',
      db: 'factory'
    }
  ]
});

// 로그 컬렉션 생성 (TTL 인덱스 포함 - 30일 후 자동 삭제)
db.createCollection('application_logs', {
  capped: false
});

// TTL 인덱스 생성 (30일 후 자동 삭제)
db.application_logs.createIndex(
  { "createdAt": 1 }, 
  { expireAfterSeconds: 2592000 } // 30일 = 30 * 24 * 60 * 60
);

// 사용자 활동 로그 컬렉션
db.createCollection('user_activity_logs', {
  capped: false
});

// 사용자 활동 로그 TTL 인덱스 (90일 후 삭제)
db.user_activity_logs.createIndex(
  { "timestamp": 1 }, 
  { expireAfterSeconds: 7776000 } // 90일
);

// 에러 로그 컬렉션 (더 긴 보관 기간)
db.createCollection('error_logs', {
  capped: false
});

// 에러 로그 TTL 인덱스 (180일 후 삭제)
db.error_logs.createIndex(
  { "occurredAt": 1 }, 
  { expireAfterSeconds: 15552000 } // 180일
);

// 뉴스 데이터 컬렉션 생성
db.createCollection('news_articles', {
  capped: false
});

// 뉴스 기사 인덱스 생성
db.news_articles.createIndex({ "title": "text", "content": "text", "summary": "text" });
db.news_articles.createIndex({ "publishedAt": -1 });
db.news_articles.createIndex({ "category": 1 });
db.news_articles.createIndex({ "source": 1 });
db.news_articles.createIndex({ "url": 1 }, { unique: true });

// 언론사 정보 컬렉션
db.createCollection('media_outlets', {
  capped: false
});

db.media_outlets.createIndex({ "name": 1 }, { unique: true });
db.media_outlets.createIndex({ "domain": 1 }, { unique: true });

// 기자 정보 컬렉션
db.createCollection('journalists', {
  capped: false
});

db.journalists.createIndex({ "name": 1 });
db.journalists.createIndex({ "mediaOutletId": 1 });

// 크롤링 메타데이터 컬렉션
db.createCollection('crawling_metadata', {
  capped: false
});

db.crawling_metadata.createIndex({ "source": 1, "lastCrawledAt": -1 });

// 인덱스 확인
print("Created indexes for news collections:");
db.news_articles.getIndexes().forEach(printjson);
db.media_outlets.getIndexes().forEach(printjson); 
db.journalists.getIndexes().forEach(printjson);
db.crawling_metadata.getIndexes().forEach(printjson);

print("Created indexes for log collections:");
db.application_logs.getIndexes().forEach(printjson);
db.user_activity_logs.getIndexes().forEach(printjson); 
db.error_logs.getIndexes().forEach(printjson);

print("MongoDB initialization completed successfully!");