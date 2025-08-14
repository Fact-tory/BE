package com.commonground.be.domain.news.service.search;

import com.commonground.be.domain.news.dto.search.SearchResult;
import com.commonground.be.domain.news.entity.News;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.action.delete.DeleteRequest;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.WriteRequest;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.MultiMatchQueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.rest.RestStatus;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.opensearch.search.sort.SortOrder;
import org.opensearch.common.unit.Fuzziness;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class OpenSearchIndexingService {

	private final RestHighLevelClient openSearchClient;
	private final ObjectMapper objectMapper;

	private static final String NEWS_INDEX = "news";

	@Async("generalTaskExecutor")
	public CompletableFuture<Void> indexNews(News news) {
		try {
			// OpenSearch 연결 상태 확인
			if (!isOpenSearchAvailable()) {
				log.debug("OpenSearch 연결 불가, 인덱싱 건너뜀: newsId={}", news.getId());
                return CompletableFuture.completedFuture(null);
			}

			Map<String, Object> document = convertToSearchDocument(news);

			IndexRequest request = new IndexRequest(NEWS_INDEX)
					.id(news.getId())
					.source(document, XContentType.JSON)
					.setRefreshPolicy(WriteRequest.RefreshPolicy.WAIT_UNTIL);

			IndexResponse response = openSearchClient.index(request, RequestOptions.DEFAULT);

			if (response.status() == RestStatus.CREATED || response.status() == RestStatus.OK) {
				log.debug("뉴스 인덱싱 성공: newsId={}", news.getId());
			}

		} catch (Exception e) {
			if (e.getMessage().contains("Connection refused")) {
				log.debug("OpenSearch 연결 실패로 인덱싱 건너뜀: newsId={}", news.getId());
			} else {
				log.error("뉴스 인덱싱 실패: newsId={}", news.getId(), e);
			}
		}

        return CompletableFuture.completedFuture(null);
    }

	/**
	 * OpenSearch 연결 가능 여부 확인
	 */
	private boolean isOpenSearchAvailable() {
		try {
			return openSearchClient.ping(RequestOptions.DEFAULT);
		} catch (Exception e) {
			return false;
		}
	}

	@Async("generalTaskExecutor")
	public CompletableFuture<Void> deleteNews(String newsId) {
		try {
			DeleteRequest request = new DeleteRequest(NEWS_INDEX, newsId)
					.setRefreshPolicy(WriteRequest.RefreshPolicy.WAIT_UNTIL);

			DeleteResponse response = openSearchClient.delete(request, RequestOptions.DEFAULT);

			if (response.status() == RestStatus.OK) {
				log.debug("뉴스 인덱스 삭제 성공: newsId={}", newsId);
			}

		} catch (Exception e) {
			log.error("뉴스 인덱스 삭제 실패: newsId={}", newsId, e);
		}

		return CompletableFuture.completedFuture(null);
	}

	public SearchResult searchNews(String keyword, int page, int size) {
		try {
			SearchRequest searchRequest = new SearchRequest(NEWS_INDEX);
			SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

			// 가중치를 적용한 멀티 필드 검색 쿼리
			MultiMatchQueryBuilder multiMatchQuery = QueryBuilders
					.multiMatchQuery(keyword)
					.field("title", 3.0f)        // 제목에 가장 높은 가중치 (3배)
					.field("summary", 2.0f)      // 요약에 중간 가중치 (2배)
					.field("content", 1.0f)      // 본문은 기본 가중치 (1배)
					.field("keywords", 2.5f)     // 키워드에 높은 가중치 (2.5배)
					.type(MultiMatchQueryBuilder.Type.BEST_FIELDS)
					.fuzziness(Fuzziness.AUTO)           // 오타 허용 (자동 퍼지 매칭)
					.operator(org.opensearch.index.query.Operator.OR);  // OR 연산자로 유연한 매칭

			BoolQueryBuilder boolQuery = QueryBuilders.boolQuery()
					.must(multiMatchQuery)
					.filter(QueryBuilders.termQuery("status", "PUBLISHED"))
					.mustNot(QueryBuilders.existsQuery("deletedAt"));

			sourceBuilder.query(boolQuery)
					.from((page - 1) * size)
					.size(size)
					.sort("publishedAt", SortOrder.DESC)
					.highlighter(new HighlightBuilder()
							.field("title")
							.field("content")
							.field("summary")   // 요약에서도 하이라이트 표시
							.preTags("<mark>")
							.postTags("</mark>"));

			searchRequest.source(sourceBuilder);

			SearchResponse searchResponse = openSearchClient.search(searchRequest,
					RequestOptions.DEFAULT);

			return convertSearchResponse(searchResponse, page, size);

		} catch (Exception e) {
			log.error("뉴스 검색 실패: keyword={}", keyword, e);
			return SearchResult.builder()
					.news(new ArrayList<>())
					.totalHits(0L)
					.page(page)
					.size(size)
					.hasNext(false)
					.build();
		}
	}

	private Map<String, Object> convertToSearchDocument(News news) {
		Map<String, Object> document = new HashMap<>();
		document.put("id", news.getId());
		document.put("title", news.getTitle());
		document.put("content", news.getContent());
		document.put("authorName", news.getAuthorName());
		document.put("publishedAt", news.getPublishedAt());
		document.put("crawledAt", news.getCrawledAt());
		document.put("category", news.getCategory().name());
		document.put("categoryName", news.getCategory().getKoreanName());
		document.put("mediaOutletId", news.getMediaOutletId());
		document.put("status", news.getStatus().name());
		document.put("viewCount", news.getViewCount());

		// 키워드가 없으면 제목과 요약에서 자동 추출
		List<String> keywords = news.getKeywords();
		if (keywords == null || keywords.isEmpty()) {
			keywords = extractKeywordsFromText(news.getTitle(), news.getSummary(),
					news.getContent());
		}
		document.put("keywords", keywords);

		document.put("summary", news.getSummary());
		document.put("crawlingSource", news.getCrawlingSource() != null ? news.getCrawlingSource().name() : "UNKNOWN");
		document.put("createdAt", news.getCreatedAt());
		document.put("updatedAt", news.getUpdatedAt());
		document.put("deletedAt", news.getDeletedAt());

		// 검색 성능을 위한 추가 필드들
		document.put("fullText", buildFullTextForSearch(news));  // 전체 텍스트 통합
		document.put("searchableText", buildSearchableText(news));  // 핵심 검색 텍스트

		return document;
	}

	/**
	 * 텍스트에서 자동으로 키워드 추출 (키워드가 없을 때 대안)
	 */
	private List<String> extractKeywordsFromText(String title, String summary, String content) {
		List<String> autoKeywords = new ArrayList<>();

		// 1. 제목에서 핵심 단어 추출 (높은 우선순위)
		if (title != null && !title.trim().isEmpty()) {
			String[] titleWords = title.trim().split("\\s+");
			for (String word : titleWords) {
				if (word.length() >= 2 && !isStopWord(word)) {  // 2글자 이상, 불용어 제외
					autoKeywords.add(word);
				}
			}
		}

		// 2. 요약에서 핵심 단어 추출
		if (summary != null && !summary.trim().isEmpty()) {
			String[] summaryWords = summary.trim().split("\\s+");
			for (String word : summaryWords) {
				if (word.length() >= 2 && !isStopWord(word) && !autoKeywords.contains(word)) {
					autoKeywords.add(word);
				}
			}
		}

		// 3. 카테고리 기반 기본 키워드 추가
		// 예: 경제 기사면 "경제", "금융" 등 추가

		// 최대 10개까지만 반환 (성능 고려)
		return autoKeywords.stream().limit(10).collect(java.util.stream.Collectors.toList());
	}

	/**
	 * 불용어 체크 (조사, 접속사 등 검색에 불필요한 단어들)
	 */
	private boolean isStopWord(String word) {
		List<String> stopWords = List.of("이", "그", "저", "것", "들", "은", "는", "이", "가",
				"을", "를", "에", "와", "과", "로", "으로", "의", "도");
		return stopWords.contains(word);
	}

	/**
	 * 검색용 전체 텍스트 구성 (모든 검색 가능한 내용 통합)
	 */
	private String buildFullTextForSearch(News news) {
		StringBuilder fullText = new StringBuilder();

        if (news.getTitle() != null) {
            fullText.append(news.getTitle()).append(" ");
        }
        if (news.getSummary() != null) {
            fullText.append(news.getSummary()).append(" ");
        }
		if (news.getContent() != null) {
			// 본문은 처음 500자만 추가 (인덱스 크기 최적화)
			String shortContent = news.getContent().length() > 500
					? news.getContent().substring(0, 500)
					: news.getContent();
			fullText.append(shortContent);
		}

		return fullText.toString().trim();
	}

	/**
	 * 핵심 검색 텍스트 구성 (가장 중요한 내용만)
	 */
	private String buildSearchableText(News news) {
		StringBuilder searchText = new StringBuilder();

		// 제목은 3번 반복 (가중치 효과)
		if (news.getTitle() != null) {
			searchText.append(news.getTitle()).append(" ");
			searchText.append(news.getTitle()).append(" ");
			searchText.append(news.getTitle()).append(" ");
		}

		// 요약은 2번 반복
		if (news.getSummary() != null) {
			searchText.append(news.getSummary()).append(" ");
			searchText.append(news.getSummary()).append(" ");
		}

		return searchText.toString().trim();
	}

	private SearchResult convertSearchResponse(SearchResponse response, int page, int size) {
		List<News> newsList = new ArrayList<>();

		for (SearchHit hit : response.getHits().getHits()) {
			try {
				Map<String, Object> source = hit.getSourceAsMap();
				News news = convertMapToNews(source);
				newsList.add(news);
			} catch (Exception e) {
				log.warn("검색 결과 변환 실패: hitId={}", hit.getId(), e);
			}
		}

		long totalHits = response.getHits().getTotalHits().value;
		boolean hasNext = (page * size) < totalHits;

		return SearchResult.builder()
				.news(newsList)
				.totalHits(totalHits)
				.page(page)
				.size(size)
				.hasNext(hasNext)
				.build();
	}

	@SuppressWarnings("unchecked")
	private News convertMapToNews(Map<String, Object> source) {
		return News.builder()
				.id((String) source.get("id"))
				.title((String) source.get("title"))
				.content((String) source.get("content"))
				.authorName((String) source.get("authorName"))
				.summary((String) source.get("summary"))
				.keywords((List<String>) source.get("keywords"))
				.mediaOutletId((String) source.get("mediaOutletId"))
				.viewCount(((Number) source.getOrDefault("viewCount", 0)).longValue())
				.build();
	}
}