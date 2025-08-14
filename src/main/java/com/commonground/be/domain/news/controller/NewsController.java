package com.commonground.be.domain.news.controller;

import com.commonground.be.domain.news.dto.request.CreateNewsRequest;
import com.commonground.be.domain.news.dto.request.NaverCrawlingRequest;
import com.commonground.be.domain.news.dto.request.UpdateNewsRequest;
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
import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
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

	// ==================== 뉴스 CRUD ====================

	@PostMapping
	@AdminRequired(message = "뉴스 생성은 관리자만 가능합니다")
	public ResponseEntity<HttpResponseDto> createNews(
			@RequestBody @Valid CreateNewsRequest request) {

		try {
			News news = newsService.createNews(request);
			return ResponseUtils.of(ResponseCodeEnum.NEWS_CREATE_SUCCESS, NewsResponse.from(news));

		} catch (DuplicateNewsException e) {
			return ResponseEntity.status(HttpStatus.CONFLICT)
					.body(new HttpResponseDto(409, e.getMessage()));
		} catch (InvalidNewsException e) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST)
					.body(new HttpResponseDto(400, e.getMessage()));
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
			return ResponseEntity.status(HttpStatus.NOT_FOUND)
					.body(new HttpResponseDto(404, "뉴스를 찾을 수 없습니다"));
		}
	}

	@PutMapping("/{id}")
	@AdminRequired(message = "뉴스 수정은 관리자만 가능합니다")
	public ResponseEntity<HttpResponseDto> updateNews(
			@PathVariable String id,
			@RequestBody @Valid UpdateNewsRequest request) {

		try {
			News updatedNews = newsService.updateNews(id, request);
			return ResponseEntity.ok(
					new HttpResponseDto(200, "Success", NewsResponse.from(updatedNews)));

		} catch (NewsNotFoundException e) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND)
					.body(new HttpResponseDto(400, "NEWS_NOT_FOUND", e.getMessage()));
		}
	}

	@DeleteMapping("/{id}")
	@AdminRequired(message = "뉴스 삭제는 관리자만 가능합니다")
	public ResponseEntity<HttpResponseDto> deleteNews(@PathVariable String id) {

		try {
			newsService.deleteNews(id);
			return ResponseEntity.ok(new HttpResponseDto(200, "Success", null));

		} catch (NewsNotFoundException e) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND)
					.body(new HttpResponseDto(400, "NEWS_NOT_FOUND", e.getMessage()));
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

		return ResponseEntity.ok(new HttpResponseDto(200, "Success", response));
	}

	@GetMapping("/recent")
	public ResponseEntity<HttpResponseDto> getRecentNews(
			@RequestParam(defaultValue = "20") int limit) {

		List<News> recentNews = newsService.findRecentNews(limit);
		List<NewsResponse> response = recentNews.stream()
				.map(NewsResponse::from)
				.collect(Collectors.toList());

		return ResponseEntity.ok(new HttpResponseDto(200, "Success", response));
	}

	@GetMapping("/trending")
	public ResponseEntity<HttpResponseDto> getTrendingNews(
			@RequestParam(defaultValue = "10") int limit) {

		List<News> trendingNews = newsService.findTrendingNews(limit);
		List<NewsResponse> response = trendingNews.stream()
				.map(NewsResponse::from)
				.collect(Collectors.toList());

		return ResponseEntity.ok(new HttpResponseDto(200, "Success", response));
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

		return ResponseEntity.ok(new HttpResponseDto(200, "Success", response));
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
			Throwable rootCause = e.getCause() != null ? e.getCause() : e;
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(new HttpResponseDto(500, "API_COLLECTION_FAILED", rootCause.getMessage()));
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
			Throwable rootCause = e.getCause() != null ? e.getCause() : e;
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(new HttpResponseDto(500, "WEB_CRAWLING_FAILED", rootCause.getMessage()));
		}
	}

	// ==================== 통계 ====================

	@GetMapping("/statistics")
	public ResponseEntity<HttpResponseDto> getNewsStatistics() {
		NewsStatistics statistics = newsService.getNewsStatistics();
		return ResponseEntity.ok(new HttpResponseDto(200, "Success", statistics));
	}

	@GetMapping("/statistics/categories")
	public ResponseEntity<HttpResponseDto> getCategoryStatistics() {
		CategoryStatistics statistics = newsService.getCategoryStatistics();
		return ResponseEntity.ok(new HttpResponseDto(200, "Success", statistics));
	}
}