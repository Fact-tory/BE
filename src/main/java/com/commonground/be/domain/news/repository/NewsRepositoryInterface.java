package com.commonground.be.domain.news.repository;

import com.commonground.be.domain.news.entity.News;
import com.commonground.be.domain.news.enums.CategoryEnum;
import java.util.List;
import java.util.Optional;

/**
 * News 도메인 Repository 인터페이스
 * 도메인 비즈니스 로직에 특화된 메서드만 정의
 */
public interface NewsRepositoryInterface {

	News save(News news);

	Optional<News> findById(String id);

	Optional<News> findByUrl(String url);

	List<News> findByCategory(CategoryEnum category, int page, int limit);

	List<News> findByMediaOutletId(String mediaOutletId, int page, int limit);

	List<News> findRecentNews(int limit);

	List<News> findTrendingNews(int limit);

	boolean existsByUrl(String url);
	
	boolean existsByOriginalUrl(String originalUrl);
	
	boolean existsByTitleAndAuthorNameAndCategory(String title, String authorName, CategoryEnum category);

	long countByCategory(CategoryEnum category);

	long countByMediaOutletId(String mediaOutletId);

	void delete(News news);

	void deleteById(String id);

	List<News> searchByKeyword(String keyword, int page, int limit);

	void incrementViewCount(String id);

	long getTotalCount();
}