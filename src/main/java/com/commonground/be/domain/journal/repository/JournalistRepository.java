package com.commonground.be.domain.journal;

import java.util.List;
import java.util.Optional;

public interface JournalistRepository {

	Journalist save(Journalist journalist);

	Optional<Journalist> findById(String id);

	Optional<Journalist> findByNameAndMediaOutletId(String name, String mediaOutletId);

	List<Journalist> findByMediaOutletId(String mediaOutletId);
}