package com.commonground.be.domain.user.repository;


import com.commonground.be.domain.user.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByUsername(String username);

	Optional<User> findUserByUsernameAndNameAndEmail(String username, String name, String email);

	boolean existsByUsername(String username);

	Optional<User> findByIdAndDeletedAtIsNull(Long id);

	Optional<User> findByKakaoId(Long kakaoId);

	Optional<User> findById(Long userId);
}
