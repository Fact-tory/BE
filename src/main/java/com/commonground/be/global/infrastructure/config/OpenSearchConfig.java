package com.commonground.be.global.infrastructure.config;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestClient;
import org.opensearch.client.RestClientBuilder;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.client.indices.CreateIndexRequest;
import org.opensearch.client.indices.CreateIndexResponse;
import org.opensearch.client.indices.GetIndexRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class OpenSearchConfig {

	@Value("${opensearch.host:localhost}")
	private String host;

	@Value("${opensearch.port:9200}")
	private int port;

	@Value("${opensearch.scheme:http}")
	private String scheme;

	@Value("${opensearch.username:admin}")
	private String username;

	@Value("${opensearch.password:admin}")
	private String password;

	@Bean
	public RestHighLevelClient openSearchClient() {
		try {
			final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
			credentialsProvider.setCredentials(AuthScope.ANY,
					new UsernamePasswordCredentials(username, password));

			RestClientBuilder builder = RestClient.builder(new HttpHost(host, port, scheme))
					.setHttpClientConfigCallback(httpClientBuilder ->
							httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider));

			RestHighLevelClient client = new RestHighLevelClient(builder);

			// 연결 테스트
			try {
				client.ping(RequestOptions.DEFAULT);
				log.info("OpenSearch 연결 성공: {}://{}:{}", scheme, host, port);

				// 인덱스 초기화
				initializeIndices(client);

				return client;
			} catch (Exception e) {
				log.warn("OpenSearch 연결 실패: {}://{}:{} - {}", scheme, host, port, e.getMessage());
				log.warn("OpenSearch 기능이 비활성화됩니다. 검색 기능은 사용할 수 없습니다.");

				// 연결 실패시에도 빈 클라이언트 반환 (null 대신)
				return client;
			}
		} catch (Exception e) {
			log.error("OpenSearch 클라이언트 생성 실패", e);
			throw new RuntimeException("OpenSearch 설정 오류", e);
		}
	}

	private void initializeIndices(RestHighLevelClient client) {
		try {
			// 뉴스 인덱스 생성
			createNewsIndex(client);
		} catch (Exception e) {
			log.error("OpenSearch 인덱스 초기화 실패", e);
		}
	}

	private void createNewsIndex(RestHighLevelClient client) throws IOException {
		GetIndexRequest getRequest = new GetIndexRequest("news");
		boolean exists = client.indices().exists(getRequest, RequestOptions.DEFAULT);

		if (!exists) {
			CreateIndexRequest createRequest = new CreateIndexRequest("news");

			// 인덱스 매핑 설정 - 성능 최적화된 필드 구성
			Map<String, Object> properties = new HashMap<>();

			// title 필드
			Map<String, Object> titleFields = new HashMap<>();
			titleFields.put("exact", Map.of("type", "text", "analyzer", "korean_exact"));
			titleFields.put("raw", Map.of("type", "keyword"));
			properties.put("title",
					Map.of("type", "text", "analyzer", "korean", "fields", titleFields));

			// content 필드
			properties.put("content", Map.of("type", "text", "analyzer", "korean"));

			// summary 필드
			Map<String, Object> summaryFields = new HashMap<>();
			summaryFields.put("exact", Map.of("type", "text", "analyzer", "korean_exact"));
			properties.put("summary",
					Map.of("type", "text", "analyzer", "korean", "fields", summaryFields));

			// keywords 필드
			Map<String, Object> keywordsFields = new HashMap<>();
			keywordsFields.put("text", Map.of("type", "text", "analyzer", "korean"));
			properties.put("keywords", Map.of("type", "keyword", "fields", keywordsFields));

			// 통합 텍스트 필드들
			properties.put("fullText", Map.of("type", "text", "analyzer", "korean"));
			properties.put("searchableText", Map.of("type", "text", "analyzer", "korean"));

			// authorName 필드
			Map<String, Object> authorFields = new HashMap<>();
			authorFields.put("text", Map.of("type", "text", "analyzer", "korean"));
			properties.put("authorName", Map.of("type", "keyword", "fields", authorFields));

			// 날짜 필드들 - milliseconds와 timezone 지원
			String dateFormat = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'||yyyy-MM-dd'T'HH:mm:ss.SSSX||yyyy-MM-dd'T'HH:mm:ss||yyyy-MM-dd||epoch_millis||strict_date_optional_time";
			properties.put("publishedAt", Map.of("type", "date", "format", dateFormat));
			properties.put("crawledAt", Map.of("type", "date", "format", dateFormat));
			properties.put("createdAt", Map.of("type", "date", "format", dateFormat));
			properties.put("updatedAt", Map.of("type", "date", "format", dateFormat));
			properties.put("deletedAt", Map.of("type", "date", "format", dateFormat));

			// 키워드 필드들
			properties.put("category", Map.of("type", "keyword"));
			properties.put("categoryName", Map.of("type", "keyword"));
			properties.put("status", Map.of("type", "keyword"));
			properties.put("mediaOutletId", Map.of("type", "keyword"));
			properties.put("crawlingSource", Map.of("type", "keyword"));

			// 숫자 필드
			properties.put("viewCount", Map.of("type", "long"));

			Map<String, Object> mapping = Map.of("properties", properties);

			createRequest.mapping(mapping);

			// 인덱스 설정 - 한글 분석기 및 성능 최적화 설정
			Map<String, Object> settings = new HashMap<>();
			settings.put("number_of_shards", 1);
			settings.put("number_of_replicas", 0);
			settings.put("refresh_interval", "30s");
			settings.put("max_result_window", 50000);

			// 분석기 설정
			Map<String, Object> analyzers = new HashMap<>();
			analyzers.put("korean", Map.of(
					"type", "custom",
					"tokenizer", "korean_tokenizer",
					"filter", List.of("lowercase", "korean_stop", "korean_synonym")
			));
			analyzers.put("korean_exact", Map.of(
					"type", "custom",
					"tokenizer", "keyword",
					"filter", List.of("lowercase")
			));

			Map<String, Object> tokenizers = new HashMap<>();
			tokenizers.put("korean_tokenizer", Map.of("type", "standard"));

			Map<String, Object> filters = new HashMap<>();
			filters.put("korean_stop", Map.of(
					"type", "stop",
					"stopwords", List.of("이", "그", "저", "것", "들", "은", "는", "이", "가",
							"을", "를", "에", "와", "과", "로", "으로", "의", "도",
							"에서", "부터", "까지", "만", "뿐", "조차", "마저")
			));
			filters.put("korean_synonym", Map.of(
					"type", "synonym",
					"synonyms", List.of("경제,금융", "정치,정부", "사회,시민", "문화,예술", "스포츠,체육")
			));

			Map<String, Object> analysis = new HashMap<>();
			analysis.put("analyzer", analyzers);
			analysis.put("tokenizer", tokenizers);
			analysis.put("filter", filters);

			settings.put("analysis", analysis);

			createRequest.settings(settings);

			CreateIndexResponse response = client.indices()
					.create(createRequest, RequestOptions.DEFAULT);

			if (response.isAcknowledged()) {
				log.info("뉴스 인덱스 생성 완료");
			}
		}
	}
}