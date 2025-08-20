package com.commonground.be.domain.news.repository;

import com.commonground.be.domain.news.entity.News;
import com.commonground.be.domain.news.enums.CategoryEnum;
import com.commonground.be.global.application.exception.NewsExceptions;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * News Adapter - Repository와 Service 사이의 추상화 계층
 * 비즈니스 로직에 특화된 데이터 접근 메서드 제공
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NewsAdapter implements NewsRepositoryInterface {

	private final NewsRepository newsRepository;

	@Override
	public News save(News news) {
		log.debug("뉴스 저장: title={}, category={}", news.getTitle(), news.getCategory());
		return newsRepository.save(news);
	}

	@Override
	public Optional<News> findById(String id) {
		return newsRepository.findById(id);
	}
	
	/**
	 * ID로 뉴스 조회 (예외 발생)
	 */
	public News findByIdOrThrow(String id) {
		return findById(id)
				.orElseThrow(() -> new RuntimeException("News not found with id: " + id));
	}

	@Override
	public Optional<News> findByUrl(String url) {
		return newsRepository.findByUrl(url);
	}

	@Override
	public List<News> findByCategory(CategoryEnum category, int page, int limit) {
		log.debug("카테고리별 뉴스 조회: category={}, page={}, limit={}", category, page, limit);
		return newsRepository.findByCategory(category, page, limit);
	}

	@Override
	public List<News> findByMediaOutletId(String mediaOutletId, int page, int limit) {
		log.debug("언론사별 뉴스 조회: mediaOutletId={}, page={}, limit={}", mediaOutletId, page, limit);
		return newsRepository.findByMediaOutletId(mediaOutletId, page, limit);
	}

	@Override
	public List<News> findRecentNews(int limit) {
		log.debug("최신 뉴스 조회: limit={}", limit);
		return newsRepository.findRecentNews(limit);
	}

	@Override
	public List<News> findTrendingNews(int limit) {
		log.debug("인기 뉴스 조회: limit={}", limit);
		return newsRepository.findTrendingNews(limit);
	}

	@Override
	public boolean existsByUrl(String url) {
		return newsRepository.existsByUrl(url);
	}

	@Override
	public boolean existsByOriginalUrl(String originalUrl) {
		return newsRepository.existsByOriginalUrl(originalUrl);
	}

	@Override
	public boolean existsByTitleAndAuthorNameAndCategory(String title, String authorName, CategoryEnum category) {
		return newsRepository.existsByTitleAndAuthorNameAndCategory(title, authorName, category);
	}

	@Override
	public long countByCategory(CategoryEnum category) {
		return newsRepository.countByCategory(category);
	}

	@Override
	public long countByMediaOutletId(String mediaOutletId) {
		return newsRepository.countByMediaOutletId(mediaOutletId);
	}

	@Override
	public void delete(News news) {
		log.debug("뉴스 삭제: id={}, title={}", news.getId(), news.getTitle());
		newsRepository.delete(news);
	}

	@Override
	public void deleteById(String id) {
		log.debug("뉴스 ID로 삭제: id={}", id);
		newsRepository.deleteById(id);
	}

	@Override
	public List<News> searchByKeyword(String keyword, int page, int limit) {
		log.debug("키워드 검색: keyword={}, page={}, limit={}", keyword, page, limit);
		return newsRepository.searchByKeyword(keyword, page, limit);
	}

	@Override
	public void incrementViewCount(String id) {
		log.debug("조회수 증가: id={}", id);
		newsRepository.incrementViewCount(id);
	}

	@Override
	public long getTotalCount() {
		return newsRepository.getTotalCount();
	}
	
	/**
	 * 중복 뉴스 체크 알고리즘
	 * URL, 원본 URL, 제목+저자+카테고리 조합으로 중복 검사
	 */
	public boolean isDuplicateNews(News news) {
		// 1. URL 중복 체크
		if (news.getUrl() != null && existsByUrl(news.getUrl())) {
			log.debug("URL 중복 감지: url={}", news.getUrl());
			return true;
		}
		
		// 2. 원본 URL 중복 체크 (원본 URL 필드가 있는 경우에만)
		// TODO: News 엔티티에 originalUrl 필드 추가 필요
		
		// 3. 제목+저자+카테고리 조합 중복 체크
		if (news.getTitle() != null && news.getAuthorName() != null && news.getCategory() != null) {
			if (existsByTitleAndAuthorNameAndCategory(news.getTitle(), news.getAuthorName(), news.getCategory())) {
				log.debug("제목+저자+카테고리 중복 감지: title={}, author={}, category={}", 
						news.getTitle(), news.getAuthorName(), news.getCategory());
				return true;
			}
		}
		
		return false;
	}
	
	/**
	 * 유효성 검증
	 */
	public void validateNews(News news) {
		if (news == null) {
			throw new IllegalArgumentException("뉴스 객체가 null입니다.");
		}
		
		if (news.getTitle() == null || news.getTitle().trim().isEmpty()) {
			throw new IllegalArgumentException("뉴스 제목이 필요합니다.");
		}
		
		if (news.getContent() == null || news.getContent().trim().isEmpty()) {
			throw new IllegalArgumentException("뉴스 내용이 필요합니다.");
		}
		
		if (news.getCategory() == null) {
			throw new IllegalArgumentException("뉴스 카테고리가 필요합니다.");
		}
	}
}