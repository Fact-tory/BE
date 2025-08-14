#!/bin/bash

# OpenSearch 인덱스 재생성 스크립트
# 날짜 형식 문제를 해결하기 위해 기존 인덱스를 삭제하고 새로 생성

OPENSEARCH_HOST="http://localhost:9200"
INDEX_NAME="news"

echo "🔄 OpenSearch 인덱스 재생성 시작..."

# 기존 인덱스 삭제
echo "❌ 기존 인덱스 삭제 중..."
curl -X DELETE "${OPENSEARCH_HOST}/${INDEX_NAME}" -H "Content-Type: application/json"

echo -e "\n⏳ 잠시 대기..."
sleep 2

# 새 인덱스 생성 (올바른 날짜 형식 매핑 포함)
echo "✨ 새 인덱스 생성 중..."
curl -X PUT "${OPENSEARCH_HOST}/${INDEX_NAME}" -H "Content-Type: application/json" -d '{
  "mappings": {
    "properties": {
      "title": {
        "type": "text",
        "analyzer": "korean"
      },
      "content": {
        "type": "text",
        "analyzer": "korean"
      },
      "authorName": {
        "type": "keyword"
      },
      "publishedAt": {
        "type": "date",
        "format": "yyyy-MM-dd'\''T'\''HH:mm:ss.SSS'\''Z'\''||yyyy-MM-dd'\''T'\''HH:mm:ss.SSSX||yyyy-MM-dd'\''T'\''HH:mm:ss||yyyy-MM-dd||epoch_millis||strict_date_optional_time"
      },
      "crawledAt": {
        "type": "date",
        "format": "yyyy-MM-dd'\''T'\''HH:mm:ss.SSS'\''Z'\''||yyyy-MM-dd'\''T'\''HH:mm:ss.SSSX||yyyy-MM-dd'\''T'\''HH:mm:ss||yyyy-MM-dd||epoch_millis||strict_date_optional_time"
      },
      "category": {
        "type": "keyword"
      },
      "url": {
        "type": "keyword"
      },
      "mediaOutletId": {
        "type": "keyword"
      },
      "journalistId": {
        "type": "keyword"
      },
      "crawlingSource": {
        "type": "keyword"
      },
      "status": {
        "type": "keyword"
      }
    }
  },
  "settings": {
    "analysis": {
      "analyzer": {
        "korean": {
          "type": "custom",
          "tokenizer": "nori_tokenizer",
          "filter": ["lowercase", "nori_part_of_speech"]
        }
      }
    }
  }
}'

echo -e "\n✅ OpenSearch 인덱스 재생성 완료!"
echo "🚨 주의: 기존 뉴스 데이터가 모두 삭제되었습니다. 다시 크롤링해주세요."