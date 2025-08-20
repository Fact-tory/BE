package com.commonground.be.domain.news.controller;

import com.commonground.be.domain.news.dto.request.CreateNewsRequest;
import com.commonground.be.domain.news.dto.request.NaverCrawlingRequest;
import com.commonground.be.domain.news.dto.request.UpdateNewsRequest;
import com.commonground.be.domain.news.dto.request.UrlCrawlingRequest;
import com.commonground.be.domain.news.dto.response.CategoryStatistics;
import com.commonground.be.domain.news.dto.response.CrawlingResponse;
import com.commonground.be.domain.news.dto.response.NewsResponse;
import com.commonground.be.domain.news.dto.response.NewsStatistics;
import com.commonground.be.domain.news.dto.crawling.RawNewsData;
import com.commonground.be.domain.news.entity.News;
import com.commonground.be.domain.news.enums.CategoryEnum;
import com.commonground.be.domain.news.service.NewsService;
import com.commonground.be.domain.news.service.crawling.CrawlingOrchestrationService;
import com.commonground.be.global.application.exception.NewsExceptions.DuplicateNewsException;
import com.commonground.be.global.application.exception.NewsExceptions.InvalidNewsException;
import com.commonground.be.global.application.exception.NewsExceptions.NewsNotFoundException;
import com.commonground.be.global.application.response.HttpResponseDto;
import com.commonground.be.global.application.response.ResponseCodeEnum;
import com.commonground.be.global.application.response.ResponseUtils;
import com.commonground.be.global.application.security.AdminRequired;
import com.commonground.be.global.infrastructure.messaging.CrawlingMessageService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/news")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class NewsController {

	private final NewsService newsService;
	private final CrawlingOrchestrationService crawlingOrchestrationService;
	private final CrawlingMessageService crawlingMessageService;

	// ==================== 뉴스 CRUD ====================

	@PostMapping
	@AdminRequired(message = "뉴스 생성은 관리자만 가능합니다")
	public ResponseEntity<HttpResponseDto> createNews(
			@RequestBody @Valid CreateNewsRequest request) {

		try {
			News news = newsService.createNews(request);
			return ResponseUtils.of(ResponseCodeEnum.NEWS_CREATE_SUCCESS, NewsResponse.from(news));

		} catch (DuplicateNewsException e) {
			return ResponseUtils.of(ResponseCodeEnum.BAD_REQUEST);
		} catch (InvalidNewsException e) {
			return ResponseUtils.of(ResponseCodeEnum.BAD_REQUEST);
		}
	}

	@GetMapping("/{id}")
	public ResponseEntity<HttpResponseDto> getNews(
			@PathVariable String id) {

		Optional<News> news = newsService.findNewsById(id);

		if (news.isPresent()) {
			// 조회수 증가 (비동기)
			newsService.incrementViewCount(id);

			return ResponseUtils.of(ResponseCodeEnum.NEWS_GET_SUCCESS,
					NewsResponse.from(news.get()));
		} else {
			return ResponseUtils.of(ResponseCodeEnum.NOT_FOUND);
		}
	}

	@PutMapping("/{id}")
	@AdminRequired(message = "뉴스 수정은 관리자만 가능합니다")
	public ResponseEntity<HttpResponseDto> updateNews(
			@PathVariable String id,
			@RequestBody @Valid UpdateNewsRequest request) {

		try {
			News updatedNews = newsService.updateNews(id, request);
			return ResponseUtils.of(ResponseCodeEnum.SUCCESS, NewsResponse.from(updatedNews));

		} catch (NewsNotFoundException e) {
			return ResponseUtils.of(ResponseCodeEnum.NOT_FOUND);
		}
	}

	@DeleteMapping("/{id}")
	@AdminRequired(message = "뉴스 삭제는 관리자만 가능합니다")
	public ResponseEntity<HttpResponseDto> deleteNews(@PathVariable String id) {

		try {
			newsService.deleteNews(id);
			return ResponseUtils.of(ResponseCodeEnum.SUCCESS);

		} catch (NewsNotFoundException e) {
			return ResponseUtils.of(ResponseCodeEnum.NOT_FOUND);
		}
	}

	// ==================== 뉴스 조회 ====================

	@GetMapping("/categories/{category}")
	public ResponseEntity<HttpResponseDto> getNewsByCategory(
			@PathVariable CategoryEnum category,
			@RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int limit) {

		List<News> newsList = newsService.findNewsByCategory(category, page, limit);
		List<NewsResponse> response = newsList.stream()
				.map(NewsResponse::from)
				.collect(Collectors.toList());

		return ResponseUtils.of(ResponseCodeEnum.SUCCESS, response);
	}

	@GetMapping("/recent")
	public ResponseEntity<HttpResponseDto> getRecentNews(
			@RequestParam(defaultValue = "20") int limit) {

		List<News> recentNews = newsService.findRecentNews(limit);
		List<NewsResponse> response = recentNews.stream()
				.map(NewsResponse::from)
				.collect(Collectors.toList());

		return ResponseUtils.of(ResponseCodeEnum.SUCCESS, response);
	}

	@GetMapping("/trending")
	public ResponseEntity<HttpResponseDto> getTrendingNews(
			@RequestParam(defaultValue = "10") int limit) {

		List<News> trendingNews = newsService.findTrendingNews(limit);
		List<NewsResponse> response = trendingNews.stream()
				.map(NewsResponse::from)
				.collect(Collectors.toList());

		return ResponseUtils.of(ResponseCodeEnum.SUCCESS, response);
	}

	@GetMapping("/search")
	public ResponseEntity<HttpResponseDto> searchNews(
			@RequestParam String keyword,
			@RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int limit) {

		List<News> searchResults = newsService.searchNews(keyword, page, limit);
		List<NewsResponse> response = searchResults.stream()
				.map(NewsResponse::from)
				.collect(Collectors.toList());

		return ResponseUtils.of(ResponseCodeEnum.SUCCESS, response);
	}

	// ==================== 뉴스 수집 ====================

	@PostMapping("/collect/naver-api")
	@AdminRequired(message = "네이버 API 뉴스 수집은 관리자만 가능합니다")
	public ResponseEntity<HttpResponseDto> collectFromNaverApi(
			@RequestBody @Valid NaverCrawlingRequest request) {

		try {
			CompletableFuture<List<News>> futureResult = newsService.collectFromNaverApi(request);
			List<News> collectedNews = futureResult.get();

			CrawlingResponse response = CrawlingResponse.builder()
					.totalCrawled(collectedNews.size())
					.successfulNews(collectedNews.stream()
							.map(NewsResponse::from)
							.collect(Collectors.toList()))
					.build();

			return ResponseUtils.of(ResponseCodeEnum.CRAWLING_EXECUTE_SUCCESS, response);

		} catch (Exception e) {
			log.error("네이버 API 뉴스 수집 컨트롤러 에러", e);
			return ResponseUtils.of(ResponseCodeEnum.INTERNAL_SERVER_ERROR);
		}
	}

	@PostMapping("/crawl/naver")
	@AdminRequired(message = "웹 크롤링은 관리자만 가능합니다")
	public ResponseEntity<HttpResponseDto> crawlNaverNews(
			@RequestBody @Valid NaverCrawlingRequest request) {

		try {
			// 새로운 RabbitMQ 기반 크롤링 오케스트레이션 사용
			CompletableFuture<List<RawNewsData>> futureResult = crawlingOrchestrationService.orchestrateCrawling(request);
			List<RawNewsData> crawledRawData = futureResult.get();

			CrawlingResponse response = CrawlingResponse.builder()
					.totalCrawled(crawledRawData.size())
					.successfulNews(crawledRawData.stream()
							.map(rawData -> NewsResponse.builder()
									.title(rawData.getTitle())
									.content(rawData.getContent())
									.url(rawData.getUrl())
									.authorName(rawData.getAuthorName())
									.publishedAt(rawData.getPublishedAt())
									.build())
							.collect(Collectors.toList()))
					.build();

			return ResponseUtils.of(ResponseCodeEnum.CRAWLING_EXECUTE_SUCCESS, response);

		} catch (Exception e) {
			log.error("웹 크롤링 컨트롤러 에러", e);
			return ResponseUtils.of(ResponseCodeEnum.INTERNAL_SERVER_ERROR);
		}
	}

	@PostMapping("/crawl/url")
	@AdminRequired(message = "URL 크롤링은 관리자만 가능합니다")
	public ResponseEntity<HttpResponseDto> crawlUrl(
			@RequestBody @Valid UrlCrawlingRequest request) {
		
		try {
			// RabbitMQ를 통한 비동기 URL 크롤링 요청
			crawlingMessageService.sendUrlCrawlingRequest(request);
			
			return ResponseUtils.of(ResponseCodeEnum.CRAWLING_EXECUTE_SUCCESS, 
				Map.of(
					"sessionId", request.getSessionId(),
					"url", request.getUrl(),
					"priority", request.getPriority(),
					"status", "QUEUED",
					"message", "URL 크롤링 요청이 큐에 등록되었습니다"
				));
				
		} catch (Exception e) {
			log.error("URL 크롤링 요청 실패", e);
			return ResponseUtils.of(ResponseCodeEnum.INTERNAL_SERVER_ERROR);
		}
	}

	// ==================== 통계 ====================

	@GetMapping("/statistics")
	public ResponseEntity<HttpResponseDto> getNewsStatistics() {
		NewsStatistics statistics = newsService.getNewsStatistics();
		return ResponseUtils.of(ResponseCodeEnum.SUCCESS, statistics);
	}

	@GetMapping("/statistics/categories")
	public ResponseEntity<HttpResponseDto> getCategoryStatistics() {
		CategoryStatistics statistics = newsService.getCategoryStatistics();
		return ResponseUtils.of(ResponseCodeEnum.SUCCESS, statistics);
	}
	
	// ==================== 추가 API 구현 ====================
	
	/**
	 * 실시간 뉴스 조회
	 */
	@GetMapping("/realtime")
	public ResponseEntity<HttpResponseDto> getRealtimeNews(
			@RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int limit,
			@RequestParam(required = false) String category,
			@RequestParam(defaultValue = "latest") String sort) {
		
		try {
			List<News> realtimeNews = newsService.findRecentNews(limit);
			List<NewsResponse> response = realtimeNews.stream()
				.map(NewsResponse::from)
				.collect(Collectors.toList());
			
			return ResponseUtils.of(ResponseCodeEnum.SUCCESS, response);
			
		} catch (Exception e) {
			log.error("실시간 뉴스 조회 실패", e);
			return ResponseUtils.of(ResponseCodeEnum.INTERNAL_SERVER_ERROR);
		}
	}
	
	/**
	 * 뉴스 상세 조회 (조회수 증가 포함)
	 */
	@GetMapping("/detail/{id}")
	public ResponseEntity<HttpResponseDto> getNewsDetail(@PathVariable String id) {
		try {
			Optional<News> news = newsService.findNewsById(id);
			
			if (news.isPresent()) {
				// 조회수 증가 (비동기)
				newsService.incrementViewCount(id);
				
				return ResponseUtils.of(ResponseCodeEnum.SUCCESS, NewsResponse.from(news.get()));
			} else {
				return ResponseUtils.of(ResponseCodeEnum.NOT_FOUND);
			}
			
		} catch (Exception e) {
			log.error("뉴스 상세 조회 실패: id={}", id, e);
			return ResponseUtils.of(ResponseCodeEnum.INTERNAL_SERVER_ERROR);
		}
	}
}