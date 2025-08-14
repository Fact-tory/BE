package com.commonground.be.domain.journal.repository;

import com.commonground.be.domain.journal.entity.Journalist;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public interface JournalistRepository {

	Journalist save(Journalist journalist);

	Optional<Journalist> findById(String id);

	Optional<Journalist> findByNameAndMediaOutletId(String name, String mediaOutletId);

	List<Journalist> findByMediaOutletId(String mediaOutletId);
}