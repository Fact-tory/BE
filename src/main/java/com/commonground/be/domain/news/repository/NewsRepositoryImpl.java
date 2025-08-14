package com.commonground.be.domain.news.repository;

import com.commonground.be.domain.news.entity.News;
import com.commonground.be.domain.news.enums.CategoryEnum;
import com.commonground.be.domain.news.enums.NewsStatusEnum;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class NewsRepositoryImpl implements NewsRepository {

	private final MongoTemplate mongoTemplate;
	private final RedisTemplate<String, Object> redisTemplate;

	@Override
	public News save(News news) {
		if (news.getId() == null) {
			news.assignId(ObjectId.get().toString());
		}
		return mongoTemplate.save(news);
	}

	@Override
	public Optional<News> findById(String id) {
		News news = mongoTemplate.findById(id, News.class);
		return Optional.ofNullable(news);
	}

	@Override
	public Optional<News> findByUrl(String url) {
		Query query = new Query(Criteria.where("url").is(url)
				.and("deletedAt").isNull());
		News news = mongoTemplate.findOne(query, News.class);
		return Optional.ofNullable(news);
	}

	@Override
	public List<News> findByCategory(CategoryEnum category, int page, int limit) {
		Query query = new Query(Criteria.where("category").is(category)
				.and("status").is(NewsStatusEnum.PUBLISHED)
				.and("deletedAt").isNull())
				.with(Sort.by(Sort.Direction.DESC, "publishedAt"))
				.skip((long) (page - 1) * limit)
				.limit(limit);

		return mongoTemplate.find(query, News.class);
	}

	@Override
	public List<News> findByMediaOutletId(String mediaOutletId, int page, int limit) {
		Query query = new Query(Criteria.where("mediaOutletId").is(mediaOutletId)
				.and("status").is(NewsStatusEnum.PUBLISHED)
				.and("deletedAt").isNull())
				.with(Sort.by(Sort.Direction.DESC, "publishedAt"))
				.skip((long) (page - 1) * limit)
				.limit(limit);

		return mongoTemplate.find(query, News.class);
	}

	@Override
	@SuppressWarnings("unchecked")
	public List<News> findRecentNews(int limit) {
		// Redis 캐시 확인
		String cacheKey = "recent_news:" + limit;
		List<News> cachedNews = (List<News>) redisTemplate.opsForValue().get(cacheKey);

		if (cachedNews != null) {
			return cachedNews;
		}

		// MongoDB에서 조회
		Query query = new Query(Criteria.where("status").is(NewsStatusEnum.PUBLISHED)
				.and("deletedAt").isNull())
				.with(Sort.by(Sort.Direction.DESC, "publishedAt"))
				.limit(limit);

		List<News> recentNews = mongoTemplate.find(query, News.class);

		// Redis에 캐시 (TTL: 10분)
		redisTemplate.opsForValue().set(cacheKey, recentNews, Duration.ofMinutes(10));

		return recentNews;
	}

	@Override
	public List<News> findTrendingNews(int limit) {
		Query query = new Query(Criteria.where("status").is(NewsStatusEnum.PUBLISHED)
				.and("publishedAt").gte(LocalDateTime.now().minusHours(24))
				.and("deletedAt").isNull())
				.with(Sort.by(Sort.Direction.DESC, "viewCount"))
				.limit(limit);

		return mongoTemplate.find(query, News.class);
	}

	@Override
	public boolean existsByUrl(String url) {
		Query query = new Query(Criteria.where("url").is(url)
				.and("deletedAt").isNull());
		return mongoTemplate.exists(query, News.class);
	}

	@Override
	public long countByCategory(CategoryEnum category) {
		Query query = new Query(Criteria.where("category").is(category)
				.and("status").is(NewsStatusEnum.PUBLISHED)
				.and("deletedAt").isNull());
		return mongoTemplate.count(query, News.class);
	}

	@Override
	public long countByMediaOutletId(String mediaOutletId) {
		Query query = new Query(Criteria.where("mediaOutletId").is(mediaOutletId)
				.and("status").is(NewsStatusEnum.PUBLISHED)
				.and("deletedAt").isNull());
		return mongoTemplate.count(query, News.class);
	}

	@Override
	public void deleteById(String id) {
		Query query = new Query(Criteria.where("id").is(id));
		Update update = new Update()
				.set("deletedAt", LocalDateTime.now())
				.set("status", NewsStatusEnum.DELETED);
		mongoTemplate.updateFirst(query, update, News.class);
	}

	@Override
	public List<News> searchByKeyword(String keyword, int page, int limit) {
		// MongoDB 텍스트 검색 또는 OpenSearch 연동
		TextCriteria criteria = TextCriteria.forDefaultLanguage()
				.matchingAny(keyword);

		Query query = new Query(criteria)
				.addCriteria(Criteria.where("status").is(NewsStatusEnum.PUBLISHED)
						.and("deletedAt").isNull())
				.with(Sort.by(Sort.Direction.DESC, "publishedAt"))
				.skip((long) (page - 1) * limit)
				.limit(limit);

		return mongoTemplate.find(query, News.class);
	}

	@Override
	public boolean existsByOriginalUrl(String originalUrl) {
		Query query = new Query(Criteria.where("crawlingMetadata.originalUrl").is(originalUrl)
				.and("deletedAt").isNull());
		return mongoTemplate.exists(query, News.class);
	}

	@Override
	public boolean existsByTitleAndAuthorNameAndCategory(String title, String authorName, CategoryEnum category) {
		Query query = new Query(Criteria.where("title").is(title)
				.and("authorName").is(authorName)
				.and("category").is(category)
				.and("deletedAt").isNull());
		return mongoTemplate.exists(query, News.class);
	}
}