package com.commonground.be.domain.media.repository;

import com.commonground.be.domain.media.entity.MediaOutlet;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public interface MediaOutletRepository {

	MediaOutlet save(MediaOutlet mediaOutlet);

	Optional<MediaOutlet> findById(String id);

	Optional<MediaOutlet> findByDomain(String domain);

	List<MediaOutlet> findAll();

	List<MediaOutlet> findByIsActive(boolean isActive);
}
