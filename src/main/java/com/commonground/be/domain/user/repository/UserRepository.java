package com.commonground.be.domain.user.repository;


import com.commonground.be.domain.user.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

	// 활성 사용자만 조회 (소프트 삭제되지 않은)
	Optional<User> findByUsernameAndDeletedAtIsNull(String username);

	Optional<User> findUserByUsernameAndNameAndEmailAndDeletedAtIsNull(String username, String name, String email);

	boolean existsByUsernameAndDeletedAtIsNull(String username);

	Optional<User> findByIdAndDeletedAtIsNull(Long id);

	Optional<User> findByEmailAndDeletedAtIsNull(String email);

	// 원본 메서드들 (삭제된 사용자 포함, 관리자용)
	Optional<User> findByUsername(String username);
	Optional<User> findByEmail(String email);
	Optional<User> findById(Long userId);
	boolean existsByUsername(String username);
}
