package com.commonground.be.domain.media.repository;

import com.commonground.be.domain.media.entity.MediaOutlet;
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
public class MediaOutletRepositoryImpl implements MediaOutletRepository {

	private final MongoTemplate mongoTemplate;

	@Override
	public MediaOutlet save(MediaOutlet mediaOutlet) {
		if (mediaOutlet.getId() == null) {
			mediaOutlet.assignId(ObjectId.get().toString());
		}
		return mongoTemplate.save(mediaOutlet);
	}

	@Override
	public Optional<MediaOutlet> findById(String id) {
		MediaOutlet outlet = mongoTemplate.findById(id, MediaOutlet.class);
		return Optional.ofNullable(outlet);
	}

	@Override
	public Optional<MediaOutlet> findByDomain(String domain) {
		Query query = new Query(Criteria.where("domain").is(domain)
				.and("deletedAt").isNull());
		MediaOutlet outlet = mongoTemplate.findOne(query, MediaOutlet.class);
		return Optional.ofNullable(outlet);
	}

	@Override
	public List<MediaOutlet> findAll() {
		Query query = new Query(Criteria.where("deletedAt").isNull())
				.with(Sort.by(Sort.Direction.ASC, "name"));
		return mongoTemplate.find(query, MediaOutlet.class);
	}

	@Override
	public List<MediaOutlet> findByIsActive(boolean isActive) {
		Query query = new Query(Criteria.where("isActive").is(isActive)
				.and("deletedAt").isNull())
				.with(Sort.by(Sort.Direction.ASC, "name"));
		return mongoTemplate.find(query, MediaOutlet.class);
	}
}