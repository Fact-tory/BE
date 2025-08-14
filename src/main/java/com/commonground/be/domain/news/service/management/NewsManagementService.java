package com.commonground.be.domain.news.service.management;

import com.commonground.be.domain.journal.entity.Journalist;
import com.commonground.be.domain.journal.repository.JournalistRepository;
import com.commonground.be.domain.media.entity.MediaOutlet;
import com.commonground.be.domain.media.enums.PoliticalBiasEnum;
import com.commonground.be.domain.media.repository.MediaOutletRepository;
import com.commonground.be.domain.news.dto.crawling.RawNewsData;
import com.commonground.be.domain.news.dto.request.CreateNewsRequest;
import com.commonground.be.domain.news.dto.request.UpdateNewsRequest;
import com.commonground.be.domain.news.entity.News;
import com.commonground.be.domain.news.enums.CrawlingPlatformEnum;
import com.commonground.be.domain.news.repository.NewsRepository;
import com.commonground.be.domain.news.service.search.OpenSearchIndexingService;
import java.net.URL;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 📋 뉴스 관리 서비스 (CRUD + 엔티티 생성/관리)
 * <p>
 * 책임: - 뉴스 CRUD 작업 - MediaOutlet, Journalist 엔티티 생성/관리 - 캐시 관리 - 뉴스 후처리 작업
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class NewsManagementService {

	private final NewsRepository newsRepository;
	private final MediaOutletRepository mediaOutletRepository;
	private final JournalistRepository journalistRepository;
	private final RedisTemplate<String, Object> redisTemplate;
	private final OpenSearchIndexingService openSearchService;

	// ==================== CRUD 메서드 ====================

	public News createNews(CreateNewsRequest request) {
		log.info("뉴스 생성 요청: title={}", request.getTitle());

		// 실제 News 엔티티 생성 로직을 여기에 구현
		// 현재는 기본 구현만 제공
		throw new UnsupportedOperationException("createNews 구현 필요");
	}

	public Optional<News> findNewsById(String id) {
		return newsRepository.findById(id);
	}

	public News updateNews(String id, UpdateNewsRequest request) {
		log.info("뉴스 업데이트 요청: id={}, title={}", id, request.getTitle());

		// 실제 News 업데이트 로직을 여기에 구현
		throw new UnsupportedOperationException("updateNews 구현 필요");
	}

	public void deleteNews(String id) {
		log.info("뉴스 삭제 요청: id={}", id);

		// 실제 News 삭제 로직을 여기에 구현
		throw new UnsupportedOperationException("deleteNews 구현 필요");
	}

	public void incrementViewCount(String newsId) {
		log.debug("조회수 증가: newsId={}", newsId);

		// 실제 조회수 증가 로직을 여기에 구현
		throw new UnsupportedOperationException("incrementViewCount 구현 필요");
	}

	// ==================== 엔티티 생성/관리 메서드 ====================

	/**
	 * MediaOutlet 찾기 또는 생성 (기존 데이터가 잘못된 경우 업데이트)
	 */
	public MediaOutlet findOrCreateMediaOutlet(RawNewsData rawData) {
		String domain = extractDomainFromUrl(rawData.getUrl());
		String extractedMediaName = extractMediaNameFromRawData(rawData);

		Optional<MediaOutlet> existingOutlet = mediaOutletRepository.findByDomain(domain);

		if (existingOutlet.isPresent()) {
			MediaOutlet outlet = existingOutlet.get();

			// 기존 데이터의 이름이 잘못된 경우 업데이트
			if ("언론사명_추출필요".equals(outlet.getName()) && extractedMediaName != null) {
				log.info("🔄 언론사명 업데이트: domain={}, 기존={} → 신규={}",
						domain, outlet.getName(), extractedMediaName);

				outlet = MediaOutlet.builder()
						.id(outlet.getId())  // 기존 ID 유지
						.name(extractedMediaName)
						.domain(outlet.getDomain())
						.website(outlet.getWebsite())
						.politicalBias(outlet.getPoliticalBias())
						.crawlingPlatform(outlet.getCrawlingPlatform())
						.crawlingUrl(outlet.getCrawlingUrl())
						.isActive(outlet.getIsActive())
						.build();

				MediaOutlet updatedOutlet = mediaOutletRepository.save(outlet);
				log.info("✅ 언론사명 업데이트 완료: id={}, name={}", updatedOutlet.getId(),
						updatedOutlet.getName());
				return updatedOutlet;
			}

			log.debug("🔍 기존 언론사 사용: id={}, name={}", outlet.getId(), outlet.getName());
			return outlet;
		}

		// 새로운 MediaOutlet 생성
		String mediaName = extractedMediaName != null ? extractedMediaName
				: extractMediaNameFromUrl(rawData.getUrl());
		log.info("✨ 새로운 언론사 생성: name={}, domain={}", mediaName, domain);

		MediaOutlet newOutlet = MediaOutlet.builder()
				.name(mediaName)
				.domain(domain)
				.website("https://" + domain)
				.politicalBias(PoliticalBiasEnum.NEUTRAL)
				.crawlingPlatform(CrawlingPlatformEnum.NAVER_NEWS)
				.crawlingUrl(rawData.getUrl())
				.isActive(true)
				.build();

		MediaOutlet savedOutlet = mediaOutletRepository.save(newOutlet);
		log.info("🏢 언론사 생성 완료: id={}, name={}", savedOutlet.getId(), savedOutlet.getName());
		return savedOutlet;
	}

	/**
	 * Journalist 찾기 또는 생성
	 */
	public Journalist findOrCreateJournalist(RawNewsData rawData, MediaOutlet mediaOutlet) {
		if (rawData.getAuthorName() == null || rawData.getAuthorName().trim().isEmpty()) {
			log.info("📰 기자 정보 없음, 기본 기자 생성: source={}, url={}", rawData.getSource(),
					rawData.getUrl());
			return findOrCreateAnonymousJournalist(mediaOutlet);
		}

		String authorName = rawData.getAuthorName().trim();
		log.debug("🔍 기자 정보 처리: authorName={}, mediaOutletId={}", authorName, mediaOutlet.getId());

		return journalistRepository.findByNameAndMediaOutletId(authorName, mediaOutlet.getId())
				.orElseGet(() -> {
					log.info("✨ 새로운 기자 생성: name={}, mediaOutlet={}", authorName,
							mediaOutlet.getName());
					Journalist newJournalist = Journalist.builder()
							.name(authorName)
							.mediaOutletId(mediaOutlet.getId())
							.isActive(true)
							.build();

					Journalist savedJournalist = journalistRepository.save(newJournalist);
					log.info("👤 기자 생성 완료: id={}, name={}", savedJournalist.getId(),
							savedJournalist.getName());
					return savedJournalist;
				});
	}

	/**
	 * 기자 정보가 없는 경우 기본 익명 기자 생성 또는 조회
	 */
	private Journalist findOrCreateAnonymousJournalist(MediaOutlet mediaOutlet) {
		String anonymousName = "편집부";

		return journalistRepository.findByNameAndMediaOutletId(anonymousName, mediaOutlet.getId())
				.orElseGet(() -> {
					log.info("✨ 익명 기자 생성: name={}, mediaOutlet={}", anonymousName,
							mediaOutlet.getName());
					Journalist anonymousJournalist = Journalist.builder()
							.name(anonymousName)
							.mediaOutletId(mediaOutlet.getId())
							.isActive(true)
							.build();

					Journalist savedJournalist = journalistRepository.save(anonymousJournalist);
					log.info("👤 익명 기자 생성 완료: id={}, name={}", savedJournalist.getId(),
							savedJournalist.getName());
					return savedJournalist;
				});
	}

	// ==================== 후처리 및 캐시 관리 ====================

	/**
	 * 뉴스 저장 후 후처리 작업
	 */
	@Async
	public void processNewsAfterSave(News news) {
		try {
			// OpenSearch 인덱싱
			openSearchService.indexNews(news);

			// 실시간 캐시 갱신
			invalidateNewsCache();

		} catch (Exception e) {
			log.error("뉴스 후처리 실패: newsId={}", news.getId(), e);
		}
	}

	/**
	 * 뉴스 관련 캐시 무효화
	 */
	public void invalidateNewsCache() {
		Set<String> keys = redisTemplate.keys("recent_news:*");
		if (keys != null && !keys.isEmpty()) {
			redisTemplate.delete(keys);
		}
	}

	// ==================== 유틸리티 메서드 ====================

	private String extractDomainFromUrl(String url) {
		try {
			return new URL(url).getHost();
		} catch (Exception e) {
			return "unknown.com";
		}
	}

	/**
	 * RawNewsData의 metadata에서 언론사명(mediaName) 추출
	 */
	private String extractMediaNameFromRawData(RawNewsData rawData) {
		if (rawData.getMetadata() == null) {
			log.debug("RawNewsData metadata가 null입니다.");
			return null;
		}

		Object mediaNameObj = rawData.getMetadata().get("mediaName");
		if (mediaNameObj instanceof String mediaName) {
			if (mediaName != null && !mediaName.trim().isEmpty()) {
				log.debug("언론사명 추출 성공: {}", mediaName);
				return mediaName.trim();
			}
		}

		log.debug("metadata에서 언론사명을 찾을 수 없습니다: {}", rawData.getMetadata());
		return null;
	}

	private String extractMediaNameFromUrl(String url) {
		return "언론사명_추출필요"; // 실제 구현 필요
	}
}