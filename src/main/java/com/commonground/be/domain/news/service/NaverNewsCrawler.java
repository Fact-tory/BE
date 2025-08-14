package com.commonground.be.domain.news.service;

import com.commonground.be.domain.news.dto.crawling.CrawlingProgress;
import com.commonground.be.domain.news.dto.crawling.RawNewsData;
import com.commonground.be.domain.news.dto.request.NaverCrawlingRequest;
import com.commonground.be.domain.news.entity.metadata.ArticleMetadata;
import com.commonground.be.global.application.exception.CrawlerExceptions;
import com.commonground.be.global.infrastructure.concurrency.RedisLock;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class NaverNewsCrawler {

	private Playwright playwright;
	private Browser browser;
	private final WebSocketProgressService progressService;
	private final RedisTemplate<String, Object> redisTemplate;

	@PostConstruct
	public void initialize() {
		try {
			this.playwright = Playwright.create();
			this.browser = playwright.chromium().launch(
					new BrowserType.LaunchOptions()
							.setHeadless(true)
							.setArgs(Arrays.asList(
									"--no-sandbox",
									"--disable-dev-shm-usage",
									"--disable-blink-features=AutomationControlled",
									"--disable-web-security",
									"--disable-features=VizDisplayCompositor"
							))
			);
			log.info("Playwright 브라우저 초기화 완료");
		} catch (Exception e) {
			log.error("Playwright 초기화 실패", e);
			throw new RuntimeException("크롤러 초기화 실패", e);
		}
	}

	@PreDestroy
	public void cleanup() {
		if (browser != null) {
			browser.close();
		}
		if (playwright != null) {
			playwright.close();
		}
	}

	/**
	 * 네이버 뉴스 크롤링 메인 메서드 (Redis 락으로 동시성 제어)
	 */
	@Async("crawlingTaskExecutor")
	@RedisLock(
			key = "'naver_crawling:' + #request.officeId + ':' + #request.categoryId",
			waitTime = 5,
			leaseTime = 300,  // 5분 - 크롤링 작업이 길 수 있음
			timeoutMessage = "해당 언론사의 크롤링이 이미 진행 중입니다. 잠시 후 다시 시도해주세요."
	)
	public CompletableFuture<List<RawNewsData>> crawlNews(NaverCrawlingRequest request) {

		String sessionId = request.getSessionId() != null ?
				request.getSessionId() : UUID.randomUUID().toString();

		Instant startTime = Instant.now();

		BrowserContext context = null;
		Page page = null;

		try {
			// BrowserContext 생성 시 tracing 비활성화
			context = browser.newContext(new Browser.NewContextOptions()
					.setUserAgent(
							"Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"));

			page = context.newPage();

			log.info("네이버 뉴스 크롤링 시작: officeId={}, categoryId={}, maxArticles={}",
					request.getOfficeId(), request.getCategoryId(), request.getMaxArticles());

			// 1단계: 기사 URL 수집
			updateProgress(sessionId, "discovering_urls", 10,
					"네이버 뉴스 기사 URL 수집 중...", 0, 0, 0, 0);

			List<String> articleUrls = discoverArticleUrls(page, request, sessionId);

			updateProgress(sessionId, "urls_discovered", 20,
					String.format("%d개 기사 URL 발견 완료", articleUrls.size()),
					articleUrls.size(), 0, 0, 0);

			// 2단계: 개별 기사 크롤링
			updateProgress(sessionId, "crawling_articles", 25,
					"개별 기사 크롤링 시작...", articleUrls.size(), 0, 0, 0);

			List<RawNewsData> rawDataList = crawlArticleDetails(page, articleUrls, request,
					sessionId);

			// 3단계: 데이터 정제 및 검증
			updateProgress(sessionId, "validating", 90,
					"크롤링 데이터 검증 중...", articleUrls.size(), rawDataList.size(), 0, 0);

			List<RawNewsData> validatedData = validateAndCleanData(rawDataList);

			Duration duration = Duration.between(startTime, Instant.now());

			updateProgress(sessionId, "completed", 100,
					String.format("크롤링 완료! %d개 기사 수집 (소요시간: %d초)",
							validatedData.size(), duration.toSeconds()),
					articleUrls.size(), validatedData.size(), validatedData.size(),
					articleUrls.size() - validatedData.size());

			log.info("네이버 뉴스 크롤링 완료: {}개 기사, 소요시간: {}초",
					validatedData.size(), duration.toSeconds());

			return CompletableFuture.completedFuture(validatedData);

		} catch (Exception e) {
			log.error("네이버 뉴스 크롤링 실패: sessionId={}", sessionId, e);

			updateProgress(sessionId, "failed", 0,
					"크롤링 실패: " + e.getMessage(), 0, 0, 0, 0);

			throw CrawlerExceptions.failToCrawlNews();

		} finally {
			// 리소스 정리
			if (page != null) {
				try {
					page.close();
				} catch (Exception e) {
					log.warn("Page 닫기 실패", e);
				}
			}
			if (context != null) {
				try {
					context.close();
				} catch (Exception e) {
					log.warn("Context 닫기 실패", e);
				}
			}
		}
	}

	/**
	 * 기사 URL 수집
	 */
	private List<String> discoverArticleUrls(Page page, NaverCrawlingRequest request,
			String sessionId) {

		String mediaPageUrl = buildNaverMediaUrl(request.getOfficeId(), request.getCategoryId());
		page.navigate(mediaPageUrl);

		// 페이지 로딩 대기 (언론사 페이지의 기본 구조)
		page.waitForSelector("body", new Page.WaitForSelectorOptions().setTimeout(15000));

		Set<String> discoveredUrls = new LinkedHashSet<>();
		int maxArticles = request.getMaxArticles();
		int scrollAttempts = 0;
		int maxScrollAttempts = request.getMaxScrollAttempts();

		while (discoveredUrls.size() < maxArticles && scrollAttempts < maxScrollAttempts) {

			// 현재 페이지의 기사 URL 추출
			List<String> currentUrls = extractCurrentPageUrls(page);

			int beforeSize = discoveredUrls.size();
			discoveredUrls.addAll(currentUrls);
			int afterSize = discoveredUrls.size();

			// 진행률 업데이트
			int progress = 10 + (int) ((double) afterSize / maxArticles * 10);
			updateProgress(sessionId, "discovering_urls", Math.min(progress, 20),
					String.format("기사 URL 수집 중... (%d/%d)", afterSize, maxArticles),
					afterSize, 0, 0, 0);

			if (afterSize >= maxArticles) {
				break;
			}

			// 무한 스크롤
			boolean hasNewContent = performInfiniteScroll(page, scrollAttempts);
			if (!hasNewContent && afterSize == beforeSize) {
				log.info("더 이상 새로운 기사가 없습니다. 크롤링 종료.");
				break;
			}

			scrollAttempts++;
			randomDelay(1500, 3000);
		}

		return discoveredUrls.stream()
				.limit(maxArticles)
				.collect(Collectors.toList());
	}

	/**
	 * 현재 페이지에서 기사 URL 추출
	 */
	private List<String> extractCurrentPageUrls(Page page) {

		return page.locator("a[href*='n.news.naver.com/article/']")
				.all()
				.stream()
				.map(locator -> {
					try {
						return locator.getAttribute("href");
					} catch (Exception e) {
						return null;
					}
				})
				.filter(Objects::nonNull)
				.filter(url -> url.contains("n.news.naver.com/article/"))
				.filter(url -> !url.contains("/comment"))  // 댓글 URL 제외
				.filter(url -> !url.contains("/reaction"))  // 반응 URL 제외  
				.filter(url -> !url.contains("/reply"))    // 답글 URL 제외
				.filter(url -> !url.contains("?tabFocusDisabled=true"))  // 접근성 관련 파라미터 제외
				.map(this::cleanArticleUrl)  // URL 정제
				.filter(Objects::nonNull)
				.distinct()
				.collect(Collectors.toList());
	}

	/**
	 * 기사 URL 정제 (불필요한 파라미터 제거)
	 */
	private String cleanArticleUrl(String url) {
		if (url == null) return null;
		
		try {
			// 기본 기사 URL 패턴: https://n.news.naver.com/article/언론사ID/기사ID
			if (url.matches(".*n\\.news\\.naver\\.com/article/\\d+/\\d+.*")) {
				// 불필요한 파라미터 제거
				String cleanUrl = url.split("\\?")[0];  // 쿼리 파라미터 제거
				log.debug("✅ 유효한 기사 URL: {}", cleanUrl);
				return cleanUrl;
			} else {
				log.debug("⚠️ 잘못된 기사 URL 형식: {}", url);
				return null;
			}
		} catch (Exception e) {
			log.debug("❌ URL 정제 실패: url={}, error={}", url, e.getMessage());
			return null;
		}
	}

	/**
	 * 개별 기사 상세 크롤링
	 */
	private List<RawNewsData> crawlArticleDetails(Page mainPage, List<String> articleUrls,
			NaverCrawlingRequest request, String sessionId) {

		List<RawNewsData> rawDataList = new ArrayList<>();
		int successCount = 0;
		int failCount = 0;

		log.info("📰 개별 기사 크롤링 시작: 총 {}개 기사", articleUrls.size());

		for (int i = 0; i < articleUrls.size(); i++) {
			String articleUrl = articleUrls.get(i);

			try {
				// 진행률 업데이트
				int progress = 25 + (int) ((double) i / articleUrls.size() * 65);
				updateProgress(sessionId, "crawling_articles", progress,
						String.format("기사 크롤링 중... (%d/%d) 성공:%d 실패:%d", i + 1, articleUrls.size(),
								successCount, failCount),
						articleUrls.size(), i, successCount, failCount);

				log.info("🔄 기사 크롤링 진행: ({}/{}) url={}", i + 1, articleUrls.size(), articleUrl);

				RawNewsData rawData = crawlSingleArticle(articleUrl, request);

				if (rawData != null && isValidRawData(rawData)) {
					rawDataList.add(rawData);
					successCount++;
					log.info("✅ 기사 크롤링 성공 ({}/{}): title={}, authorName={}, contentLength={}",
							successCount, articleUrls.size(),
							rawData.getTitle() != null ? rawData.getTitle()
									.substring(0, Math.min(50, rawData.getTitle().length())) + "..."
									: "null",
							rawData.getAuthorName(),
							rawData.getContent() != null ? rawData.getContent().length() : 0);
				} else {
					failCount++;
					if (rawData != null) {
						log.warn("⚠️ 기사 유효성 검증 실패 ({}/{}): url={}, title={}, contentLength={}",
								failCount, articleUrls.size(), articleUrl, rawData.getTitle(),
								rawData.getContent() != null ? rawData.getContent().length() : 0);
					} else {
						log.warn("❌ 기사 크롤링 실패 ({}/{}): url={}", failCount, articleUrls.size(),
								articleUrl);
					}
				}

				// 네이버 차단 방지 딜레이
				randomDelay(800, 1500);

			} catch (Exception e) {
				failCount++;
				log.error("💥 개별 기사 크롤링 예외 ({}/{}): url={}, error={}", failCount, articleUrls.size(),
						articleUrl, e.getMessage());
			}
		}

		log.info("📊 개별 기사 크롤링 완료: 총 {}개 중 성공 {}개, 실패 {}개", articleUrls.size(), successCount,
				failCount);
		return rawDataList;
	}

	/**
	 * 단일 기사 크롤링
	 */
	private RawNewsData crawlSingleArticle(String articleUrl, NaverCrawlingRequest request) {
		Instant startTime = Instant.now();
		log.debug("🔍 기사 크롤링 시작: url={}", articleUrl);

		BrowserContext articleContext = null;
		Page articlePage = null;

		try {
			// 새로운 브라우저 컨텍스트 생성
			articleContext = browser.newContext(new Browser.NewContextOptions()
					.setUserAgent(
							"Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36"));
			articlePage = articleContext.newPage();

			log.debug("📄 페이지 네비게이션 시작: url={}", articleUrl);
			articlePage.navigate(articleUrl);

			// 기사 본문 영역 대기
			log.debug("⏳ 본문 영역 대기 중: url={}", articleUrl);
			try {
				articlePage.waitForSelector("#dic_area",
						new Page.WaitForSelectorOptions().setTimeout(15000));
				log.debug("✅ 본문 영역 로딩 완료: url={}", articleUrl);
			} catch (Exception e) {
				log.warn("⚠️ 본문 영역 로딩 실패, 대체 셀렉터 시도: url={}", articleUrl);
				// 대체 셀렉터들 시도
				String[] alternativeSelectors = {".newsct_article", "._article_content",
						".go_trans._article_content"};
				boolean found = false;
				for (String selector : alternativeSelectors) {
					try {
						articlePage.waitForSelector(selector,
								new Page.WaitForSelectorOptions().setTimeout(5000));
						log.debug("✅ 대체 셀렉터로 로딩 완료: selector={}, url={}", selector, articleUrl);
						found = true;
						break;
					} catch (Exception ex) {
						continue;
					}
				}
				if (!found) {
					log.error("❌ 모든 셀렉터 실패: url={}", articleUrl);
					return null;
				}
			}

			// 기사 메타데이터 추출
			ArticleMetadata metadata = extractArticleMetadata(articlePage, articleUrl);
			
			// request의 categoryId를 fallback으로 사용 (URL에서 추출되지 않은 경우)
			if (metadata.getCategoryId() == null && request.getCategoryId() != null) {
				metadata = ArticleMetadata.builder()
					.officeId(metadata.getOfficeId())
					.categoryId(request.getCategoryId())  // request에서 가져온 categoryId 사용
					.articleId(metadata.getArticleId())
					.build();
			}
			
			log.debug("📊 메타데이터 추출: officeId={}, categoryId={} (fallback: {}), articleId={}",
					metadata.getOfficeId(), metadata.getCategoryId(), 
					request.getCategoryId(), metadata.getArticleId());

			// 기사 데이터 추출
			String title = extractTitle(articlePage);
			String content = extractContent(articlePage);
			String authorName = extractAuthorName(articlePage);
			String mediaName = extractMediaName(articlePage);
			LocalDateTime publishedAt = extractPublishedDate(articlePage);
			String originalUrl = extractOriginalUrl(articlePage);

			log.debug("📝 기사 정보 추출 완료: title={}, authorName={}, mediaName={}, contentLength={}, publishedAt={}",
					title != null ? title.substring(0, Math.min(50, title.length())) + "..."
							: "null",
					authorName,
					mediaName,
					content != null ? content.length() : 0,
					publishedAt);

			RawNewsData rawData = RawNewsData.builder()
					.url(articleUrl)
					.title(title)
					.content(content)
					.authorName(authorName)
					.publishedAt(publishedAt)
					.officeId(metadata.getOfficeId())
					.categoryId(metadata.getCategoryId())
					.articleId(metadata.getArticleId())
					.originalUrl(originalUrl)
					.discoveredAt(LocalDateTime.now())
					.responseTime((int) Duration.between(startTime, Instant.now()).toMillis())
					.source("naver_crawler")
					.metadata(Map.of("mediaName", mediaName != null ? mediaName : ""))
					.build();

			log.debug("✅ 기사 크롤링 성공: url={}, 소요시간={}ms", articleUrl, rawData.getResponseTime());
			return rawData;

		} catch (Exception e) {
			Duration elapsed = Duration.between(startTime, Instant.now());
			log.error("❌ 기사 상세 크롤링 실패: url={}, 소요시간={}ms, error={}",
					articleUrl, elapsed.toMillis(), e.getMessage());
			return null;

		} finally {
			// 리소스 정리 (순서 중요: Page -> Context)
			if (articlePage != null && !articlePage.isClosed()) {
				try {
					articlePage.close();
					log.debug("🔒 페이지 정리 완료: url={}", articleUrl);
				} catch (Exception e) {
					log.warn("⚠️ 페이지 정리 실패: url={}, error={}", articleUrl, e.getMessage());
				}
			}
			if (articleContext != null) {
				try {
					articleContext.close();
					log.debug("🔒 컨텍스트 정리 완료: url={}", articleUrl);
				} catch (Exception e) {
					log.warn("⚠️ 컨텍스트 정리 실패: url={}, error={}", articleUrl, e.getMessage());
				}
			}
		}
	}

	/**
	 * 무한 스크롤 실행
	 */
	private boolean performInfiniteScroll(Page page, int attempt) {

		try {
			// 스크롤 전 유효한 기사 링크 개수 계산
			int beforeCount = countValidArticleLinks(page);

			// 페이지 끝으로 스크롤
			page.evaluate("window.scrollTo(0, document.body.scrollHeight)");

			// 로딩 대기
			int waitTimeout = Math.min(3000 + (attempt * 500), 10000);
			randomDelay(waitTimeout, waitTimeout + 1000);

			// 스크롤 후 유효한 기사 링크 개수 계산
			int afterCount = countValidArticleLinks(page);

			log.debug("스크롤 시도 {}: 유효한 기사 링크 이전={}개, 이후={}개", attempt, beforeCount, afterCount);

			return afterCount > beforeCount;

		} catch (Exception e) {
			log.warn("무한 스크롤 실행 실패: attempt={}", attempt, e);
			return false;
		}
	}

	/**
	 * 페이지에서 유효한 기사 링크 개수 계산
	 */
	private int countValidArticleLinks(Page page) {
		try {
			return (int) page.locator("a[href*='n.news.naver.com/article/']")
					.all()
					.stream()
					.map(locator -> {
						try {
							return locator.getAttribute("href");
						} catch (Exception e) {
							return null;
						}
					})
					.filter(Objects::nonNull)
					.filter(url -> url.contains("n.news.naver.com/article/"))
					.filter(url -> !url.contains("/comment"))
					.filter(url -> !url.contains("/reaction"))
					.filter(url -> !url.contains("/reply"))
					.filter(url -> !url.contains("?tabFocusDisabled=true"))
					.filter(url -> url.matches(".*n\\.news\\.naver\\.com/article/\\d+/\\d+.*"))
					.distinct()
					.count();
		} catch (Exception e) {
			log.debug("유효한 기사 링크 카운팅 실패", e);
			return 0;
		}
	}

	/**
	 * 데이터 검증 및 정제
	 */
	private List<RawNewsData> validateAndCleanData(List<RawNewsData> rawDataList) {

		return rawDataList.stream()
				.filter(this::isValidRawData)
				.map(this::cleanRawData)
				.collect(Collectors.toMap(
						// 제목 + 기자명으로 중복 체크 (같은 제목이라도 기자가 다르면 다른 기사)
						rawData -> generateDuplicateKey(rawData.getTitle(),
								rawData.getAuthorName()),
						rawData -> rawData,
						// 중복 시 발행일이 최신인 것을 선택
						(existing, replacement) -> {
							if (replacement.getPublishedAt() != null
									&& existing.getPublishedAt() != null) {
								return replacement.getPublishedAt()
										.isAfter(existing.getPublishedAt()) ?
										replacement : existing;
							}
							return existing;
						},
						LinkedHashMap::new // 순서 유지
				))
				.values()
				.stream()
				.collect(Collectors.toList());
	}

	/**
	 * 중복 검사용 키 생성 (제목 + 기자명)
	 */
	private String generateDuplicateKey(String title, String authorName) {
		String normalizedTitle = title != null ? title.trim().toLowerCase() : "";
		String normalizedAuthor = authorName != null ? authorName.trim() : "unknown";
		return normalizedTitle + "|" + normalizedAuthor;
	}

	/**
	 * 원시 데이터 유효성 검증
	 */
	private boolean isValidRawData(RawNewsData rawData) {

		if (rawData == null) {
			return false;
		}

		if (rawData.getTitle() == null || rawData.getTitle().trim().isEmpty()) {
			return false;
		}

		if (rawData.getContent() == null || rawData.getContent().trim().length() < 50) {
			return false;
		}

		if (rawData.getUrl() == null || !rawData.getUrl().contains("naver.com")) {
			return false;
		}

		return true;
	}

	/**
	 * 원시 데이터 정제
	 */
	private RawNewsData cleanRawData(RawNewsData rawData) {

		// 제목 정제
		if (rawData.getTitle() != null) {
			String cleanedTitle = cleanSpecialCharacters(rawData.getTitle());
			rawData.setTitle(HtmlUtils.htmlUnescape(cleanedTitle));
		}

		// 내용 정제
		if (rawData.getContent() != null) {
			String cleanedContent = cleanSpecialCharacters(rawData.getContent());
			rawData.setContent(cleanContent(cleanedContent));
		}

		// 기자명 정제
		if (rawData.getAuthorName() != null) {
			rawData.setAuthorName(cleanSpecialCharacters(rawData.getAuthorName()));
		}

		return rawData;
	}

	// ==================== 진행상황 업데이트 ====================

	private void updateProgress(String sessionId, String step, int progress, String message,
			int totalUrls, int crawledUrls, int successUrls, int failedUrls) {

		CrawlingProgress progressData = CrawlingProgress.builder()
				.sessionId(sessionId)
				.step(step)
				.progress(progress)
				.message(message)
				.totalUrls(totalUrls)
				.crawledUrls(crawledUrls)
				.successUrls(successUrls)
				.failedUrls(failedUrls)
				.timestamp(LocalDateTime.now())
				.build();

		// WebSocket으로 진행상황 전송
		progressService.updateProgress(sessionId, step, progress, message);

		// Redis에 진행상황 저장 (TTL: 1시간)
		String cacheKey = "crawling_progress:" + sessionId;
		redisTemplate.opsForValue().set(cacheKey, progressData, Duration.ofHours(1));
	}

	// ==================== 데이터 추출 메서드들 ====================

	private String extractTitle(Page page) {
		String[] titleSelectors = {
				".media_end_head_headline span",
				".media_end_head_headline",
				"#title_area span",
				"h1", "h2"
		};

		for (String selector : titleSelectors) {
			try {
				String title = page.locator(selector).first().textContent();
				if (title != null && !title.trim().isEmpty()) {
					return cleanTextForSearch(title.trim()); // 🧹 완전 정제 모드
				}
			} catch (Exception e) {
				// 다음 셀렉터 시도
			}
		}

		String pageTitle = page.title();
		return pageTitle != null ? cleanTextForSearch(pageTitle) : null; // 🧹 완전 정제 모드
	}

	private String extractContent(Page page) {
		try {
			String content = page.locator("#dic_area").textContent();

			if (content != null && !content.trim().isEmpty()) {
				return cleanTextForSearch(content); // 🧹 완전 정제 모드 (본문)
			}

			String[] alternativeSelectors = {
					".go_trans._article_content",
					"._article_content",
					".newsct_article"
			};

			for (String selector : alternativeSelectors) {
				try {
					String altContent = page.locator(selector).textContent();
					if (altContent != null && !altContent.trim().isEmpty()) {
						return cleanTextForSearch(altContent); // 🧹 완전 정제 모드 (본문)
					}
				} catch (Exception e) {
					// 다음 셀렉터 시도
				}
			}

		} catch (Exception e) {
			log.warn("본문 추출 실패", e);
		}

		return null;
	}


	private String extractAuthorName(Page page) {
		// 1순위: 기사 헤더의 기자 정보 박스 (가장 정확한 정보)
		String authorName = extractAuthorsBySelector(page,
				".media_end_head_journalist .media_end_head_journalist_name");
		if (authorName != null && !authorName.isEmpty()) {
			return authorName;
		}

		// 2순위: 본문 내/주변의 byline (사용자께서 알려주신 규칙 기반)
		// .byline > byline_p > byline_s 같은 구조는 .byline .byline_s 로 단순화하여 견고하게 만듭니다.
		String[] bylineSelectors = {
				".newsct_body .byline_s",
				"#contents .byline_s",
				".byline .byline_p",
				".byline" // 가장 일반적인 byline
		};
		for (String selector : bylineSelectors) {
			authorName = extractAuthorsBySelector(page, selector);
			if (authorName != null && !authorName.isEmpty()) {
				return authorName;
			}
		}

		// 3순위: 기타 일반적인 fallback 선택자들
		String[] fallbackSelectors = {
				".media_end_head_journalist_name",
				".byline_p",
				".reporter",
				".journalist",
				".author",
				"[class*='journalist']",
				"[class*='reporter']",
				"[class*='byline']"
		};
		for (String selector : fallbackSelectors) {
			authorName = extractAuthorsBySelector(page, selector);
			if (authorName != null && !authorName.isEmpty()) {
				return authorName;
			}
		}

		log.debug("⚠️ 모든 선택자로 기자명 추출 실패");
		return null;
	}

	/**
	 * 특정 CSS 선택자를 사용해 기자명 목록을 추출, 정제 후 문자열로 반환하는 헬퍼 메서드
	 */
	private String extractAuthorsBySelector(Page page, String selector) {
		try {
			List<String> authorNames = page.locator(selector).all()
					.stream()
					.map(locator -> {
						try {
							return locator.textContent();
						} catch (Exception e) {
							return null;
						}
					})
					.filter(Objects::nonNull)
					.map(this::cleanAuthorName) // 이메일, "기자" 단어 제거
					.filter(name -> name != null && !name.isEmpty())
					.distinct() // 중복 이름 제거
					.collect(Collectors.toList());

			if (!authorNames.isEmpty()) {
				String result = String.join(", ", authorNames);
				log.debug("📝 기자명 추출 성공: selector='{}', result='{}'", selector, result);
				return result;
			}
		} catch (Exception e) {
			log.debug("⚠️ 기자명 추출 시도 실패: selector='{}', error={}", selector, e.getMessage());
		}
		return null;
	}

	/**
	 * 기자명 텍스트 정제 (이메일 제거, 기자 텍스트 제거 등) - 이 메서드는 기존 코드 그대로 유지합니다.
	 */
	private String cleanAuthorName(String rawText) {
		if (rawText == null || rawText.trim().isEmpty()) {
			return null;
		}

		// 📝 기자명은 부분 정제 모드 사용 (원본 느낌 유지)
		String cleaned = cleanTextBasic(rawText)
				.replaceAll("\\s*[\\w._%+-]+@[\\w.-]+\\.[A-Za-z]{2,}\\s*", "") // 이메일 제거
				.replaceAll("\\s*기자\\s*$", "")     // 끝에 있는 "기자" 제거
				.replaceAll("\\s*기자\\s*", " ")     // 중간에 있는 "기자" → 공백
				.replaceAll("[/\\\\]+", ", ")       // 슬래시들 → 쉼표 (여러 기자명 구분)
				.replaceAll("\\s+", " ")
				.trim();

		if (cleaned.isEmpty() || cleaned.matches("^\\d+$") || cleaned.length() < 2) {
			return null;
		}
		return cleaned;
	}

	private LocalDateTime extractPublishedDate(Page page) {
		try {
			String dateTimeStr = page.locator("._ARTICLE_DATE_TIME").getAttribute("data-date-time");
			if (dateTimeStr != null) {
				DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
				return LocalDateTime.parse(dateTimeStr, formatter);
			}
		} catch (Exception e) {
			// 발행일 추출 실패시 현재 시간 사용
		}
		return LocalDateTime.now();
	}

	private String extractOriginalUrl(Page page) {
		try {
			return page.locator(".media_end_head_origin_link").getAttribute("href");
		} catch (Exception e) {
			return null;
		}
	}

	private ArticleMetadata extractArticleMetadata(Page page, String url) {
		ArticleMetadata.ArticleMetadataBuilder builder = ArticleMetadata.builder();

		try {
			if (url.contains("/article/")) {
				String[] parts = url.split("/article/")[1].split("\\?")[0].split("/");
				if (parts.length >= 2) {
					builder.officeId(parts[0]).articleId(parts[1]);
				}
			}

			if (url.contains("sid=")) {
				String categoryId = url.split("sid=")[1].split("&")[0];
				builder.categoryId(categoryId);
			}

		} catch (Exception e) {
			log.debug("메타데이터 추출 실패", e);
		}

		return builder.build();
	}

	// ==================== 텍스트 정제 메서드 ====================
	
	/**
	 * 🧹 완전 정제 모드: 검색과 저장에 최적화 (특수문자, 기호, 불필요한 텍스트 모두 제거)
	 * 사용처: 제목, 본문, 검색용 텍스트
	 */
	private String cleanTextForSearch(String rawText) {
		if (rawText == null || rawText.trim().isEmpty()) {
			return rawText;
		}

		return rawText
				// HTML 엔티티 먼저 디코딩
				.replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
				.replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ")
				// Unicode 특수문자 정규화
				.replace("\u00A0", " ").replace("\u2007", " ").replace("\u202F", " ").replace("\u3000", " ")
				// 뉴스 특화 불필요 텍스트 제거
				.replaceAll("\\[.*?\\]", "")  // [편집자주], [사진] 등
				.replaceAll("▲.*?ⓒ.*", "")  // ▲ 사진설명 ⓒ 저작권 제거
				.replaceAll("ⓒ\\s*\\w+", "")  // ⓒ 저작권 표시 제거
				.replaceAll("\\(.*?기자\\)", "")  // (기자명 기자) 제거
				// 연속 기호 정리
				.replaceAll("[/\\\\]{2,}", "/")  // 연속 슬래시 → 단일 슬래시
				.replaceAll("-{2,}", "-")     // 연속 하이픈 → 단일 하이픈
				.replaceAll("\\s+", " ")      // 연속 공백 → 단일 공백
				.replaceAll("\\n+", " ")      // 개행문자 → 공백
				.trim();
	}
	
	/**
	 * 📝 부분 정제 모드: 기본 가독성만 향상 (원본 느낌 유지)
	 * 사용처: 기자명, 언론사명, 메타데이터
	 */
	private String cleanTextBasic(String rawText) {
		if (rawText == null || rawText.trim().isEmpty()) {
			return rawText;
		}

		return rawText
				// 기본 HTML 엔티티만 디코딩
				.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
				.replace("&nbsp;", " ")
				// 기본 Unicode 공백만 정규화
				.replace("\u00A0", " ").replace("\u3000", " ")
				// 연속 공백만 정리
				.replaceAll("\\s+", " ")
				.replace("\r\n", "\n").replace("\r", "\n")
				.trim();
	}

	// 기존 메서드는 완전 정제 모드로 변경
	private String cleanContent(String rawContent) {
		return cleanTextForSearch(rawContent);
	}

	private void randomDelay(int minMs, int maxMs) {
		try {
			int delay = ThreadLocalRandom.current().nextInt(minMs, maxMs + 1);
			Thread.sleep(delay);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private String buildNaverMediaUrl(String officeId, String categoryId) {
		return String.format("https://media.naver.com/press/%s?sid=%s", officeId, categoryId);
	}

	/**
	 * 언론사명 추출 (로고 이미지의 alt 속성에서)
	 */
	private String extractMediaName(Page page) {
		String[] logoSelectors = {
				".media_end_head_top_logo_img",
				".media_end_head_top_logo img",
				".press_logo img",
				"img[alt]:not([alt=''])"
		};

		for (String selector : logoSelectors) {
			try {
				String mediaName = page.locator(selector).first().getAttribute("alt");
				if (mediaName != null && !mediaName.trim().isEmpty()) {
					// 📝 언론사명은 부분 정제 모드 사용 (원본 느낌 유지)
					String cleanedName = cleanTextBasic(mediaName);
					log.debug("✅ 언론사명 추출 성공: selector='{}', mediaName='{}'", selector, cleanedName);
					return cleanedName;
				}
			} catch (Exception e) {
				log.debug("⚠️ 언론사명 추출 시도 실패: selector='{}'", selector);
			}
		}

		log.debug("⚠️ 모든 선택자로 언론사명 추출 실패");
		return null;
	}

	// ==================== 텍스트 정제 적용 가이드 ====================
	/*
	 * 🧹 cleanTextForSearch() 사용 대상:
	 * - 제목 (title): 검색 최적화 필요
	 * - 본문 (content): 검색 최적화 필요  
	 * - 저작권, 광고, 편집자주 등 불필요한 텍스트 완전 제거
	 *
	 * 📝 cleanTextBasic() 사용 대상:
	 * - 기자명 (authorName): 원본 느낌 유지
	 * - 언론사명 (mediaName): 브랜드명 원형 보존
	 * - 메타데이터: 기본 가독성만 향상
	 */

	/**
	 * 특정 네이버 뉴스 URL에서 기자명만 추출 (API 데이터 보완용)
	 */
	public String extractAuthorNameFromUrl(String naverUrl) {
		BrowserContext context = null;
		Page page = null;

		try {
			// 빠른 크롤링을 위한 별도 컨텍스트 생성
			context = browser.newContext(new Browser.NewContextOptions()
					.setUserAgent(
							"Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36"));
			page = context.newPage();

			log.debug("🔍 네이버 URL 기자명 추출: url={}", naverUrl);
			page.navigate(naverUrl);

			// 기사 영역 로딩 대기 (짧은 타임아웃)
			try {
				page.waitForSelector("#dic_area",
						new Page.WaitForSelectorOptions().setTimeout(5000));
			} catch (Exception e) {
				log.debug("⚠️ 본문 영역 로딩 실패, 기자명 추출 계속 진행: url={}", naverUrl);
			}

			// 기자명 추출
			String authorName = extractAuthorName(page);

			if (authorName != null) {
				log.debug("✅ 네이버 URL 기자명 추출 성공: authorName={}", authorName);
			} else {
				log.debug("⚠️ 네이버 URL 기자명 추출 실패: url={}", naverUrl);
			}

			return authorName;

		} catch (Exception e) {
			log.debug("❌ 네이버 URL 기자명 추출 예외: url={}, error={}", naverUrl, e.getMessage());
			return null;
		} finally {
			// 리소스 정리
			if (page != null && !page.isClosed()) {
				try {
					page.close();
				} catch (Exception e) { /* ignore */ }
			}
			if (context != null) {
				try {
					context.close();
				} catch (Exception e) { /* ignore */ }
			}
		}
	}
}
