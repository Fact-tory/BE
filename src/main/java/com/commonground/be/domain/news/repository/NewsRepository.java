package com.commonground.be.domain.news.repository;

import com.commonground.be.domain.news.entity.News;
import com.commonground.be.domain.news.enums.CategoryEnum;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public interface NewsRepository {

	News save(News news);

	Optional<News> findById(String id);

	Optional<News> findByUrl(String url);

	List<News> findByCategory(CategoryEnum category, int page, int limit);

	List<News> findByMediaOutletId(String mediaOutletId, int page, int limit);

	List<News> findRecentNews(int limit);

	List<News> findTrendingNews(int limit);

	boolean existsByUrl(String url);

	long countByCategory(CategoryEnum category);

	long countByMediaOutletId(String mediaOutletId);

	void deleteById(String id);

	List<News> searchByKeyword(String keyword, int page, int limit);
	
	// 중복 뉴스 체크를 위한 메서드들
	boolean existsByOriginalUrl(String originalUrl);
	
	boolean existsByTitleAndAuthorNameAndCategory(String title, String authorName, CategoryEnum category);
}
