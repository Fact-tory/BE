package com.commonground.be.domain.social.repository;

import com.commonground.be.domain.social.entity.SocialAccount;
import com.commonground.be.domain.social.entity.SocialAccount.SocialProvider;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> {

    // 특정 소셜 계정 조회
    Optional<SocialAccount> findByProviderAndSocialId(SocialProvider provider, String socialId);

    // 이메일로 소셜 계정들 조회 (병합 시 사용)
    List<SocialAccount> findByEmail(String email);

    // 사용자의 모든 소셜 계정 조회
    @Query("SELECT sa FROM SocialAccount sa WHERE sa.user.id = :userId")
    List<SocialAccount> findByUserId(@Param("userId") Long userId);

    // 사용자의 특정 프로바이더 계정 조회
    @Query("SELECT sa FROM SocialAccount sa WHERE sa.user.id = :userId AND sa.provider = :provider")
    Optional<SocialAccount> findByUserIdAndProvider(@Param("userId") Long userId, 
                                                   @Param("provider") SocialProvider provider);

    // 소셜 계정 존재 여부 확인
    boolean existsByProviderAndSocialId(SocialProvider provider, String socialId);
}