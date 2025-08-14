package com.commonground.be.domain.journal.repository;

import com.commonground.be.domain.journal.entity.Journalist;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JournalistRepositoryImpl implements JournalistRepository {

	private final MongoTemplate mongoTemplate;

	@Override
	public Journalist save(Journalist journalist) {
		if (journalist.getId() == null) {
			journalist.assignId(ObjectId.get().toString());
		}
		return mongoTemplate.save(journalist);
	}

	@Override
	public Optional<Journalist> findById(String id) {
		Journalist journalist = mongoTemplate.findById(id, Journalist.class);
		return Optional.ofNullable(journalist);
	}

	@Override
	public Optional<Journalist> findByNameAndMediaOutletId(String name, String mediaOutletId) {
		Query query = new Query(Criteria.where("name").is(name)
				.and("mediaOutletId").is(mediaOutletId)
				.and("deletedAt").isNull());
		Journalist journalist = mongoTemplate.findOne(query, Journalist.class);
		return Optional.ofNullable(journalist);
	}

	@Override
	public List<Journalist> findByMediaOutletId(String mediaOutletId) {
		Query query = new Query(Criteria.where("mediaOutletId").is(mediaOutletId)
				.and("deletedAt").isNull())
				.with(Sort.by(Sort.Direction.ASC, "name"));
		return mongoTemplate.find(query, Journalist.class);
	}
}