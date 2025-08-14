package com.commonground.be.domain.social.service;

import com.commonground.be.domain.social.dto.SocialUserInfo;
import com.commonground.be.domain.social.entity.SocialAccount;
import com.commonground.be.domain.social.entity.SocialAccount.SocialProvider;
import com.commonground.be.domain.social.repository.SocialAccountRepository;
import com.commonground.be.domain.user.entity.User;
import com.commonground.be.domain.user.repository.UserRepository;
import com.commonground.be.domain.user.utils.UserRole;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SocialUserService {

    private final UserRepository userRepository;
    private final SocialAccountRepository socialAccountRepository;

    @Transactional
    public User registerSocialUserIfNeeded(SocialUserInfo socialUserInfo) {
        SocialProvider provider = SocialProvider.valueOf(socialUserInfo.getProvider().toUpperCase());
        
        // 1. 기존 소셜 계정 확인
        return socialAccountRepository.findByProviderAndSocialId(provider, socialUserInfo.getId())
                .map(socialAccount -> {
                    log.info("기존 {} 사용자 로그인 - username: {}", 
                            provider, socialAccount.getUser().getUsername());
                    return socialAccount.getUser();
                })
                .orElseGet(() -> {
                    // 2. 이메일로 기존 사용자 찾기 (안전한 병합)
                    User existingUser = findUserByEmail(socialUserInfo.getEmail());
                    
                    if (existingUser != null) {
                        // 기존 사용자에게 새 소셜 계정 연결
                        return linkSocialAccountToUser(existingUser, socialUserInfo, provider);
                    } else {
                        // 완전 신규 사용자 생성
                        return createNewUserWithSocialAccount(socialUserInfo, provider);
                    }
                });
    }

    private User findUserByEmail(String email) {
        // 이메일로 기존 소셜 계정들 찾기
        List<SocialAccount> socialAccountsWithEmail = socialAccountRepository.findByEmail(email);
        
        if (!socialAccountsWithEmail.isEmpty()) {
            User user = socialAccountsWithEmail.get(0).getUser();
            log.info("이메일로 기존 사용자 발견 - email: {}, username: {}", email, user.getUsername());
            return user;
        }
        
        // 일반 회원가입 사용자도 확인 (향후 확장성)
        return userRepository.findByEmail(email).orElse(null);
    }

    private User linkSocialAccountToUser(User user, SocialUserInfo socialUserInfo, SocialProvider provider) {
        log.info("기존 사용자에게 {} 계정 연결 - username: {}, email: {}", 
                provider, user.getUsername(), socialUserInfo.getEmail());

        SocialAccount socialAccount = SocialAccount.builder()
                .user(user)
                .provider(provider)
                .socialId(socialUserInfo.getId())
                .email(socialUserInfo.getEmail())
                .socialUsername(socialUserInfo.getName())
                .build();

        socialAccountRepository.save(socialAccount);
        log.info("{} 소셜 계정 연결 완료 - username: {}", provider, user.getUsername());
        
        return user;
    }

    private User createNewUserWithSocialAccount(SocialUserInfo socialUserInfo, SocialProvider provider) {
        log.info("신규 {} 사용자 생성 - email: {}", provider, socialUserInfo.getEmail());

        // 유니크한 username 생성
        String username = generateUniqueUsername(socialUserInfo.getName());

        User newUser = User.builder()
                .username(username)
                .name(socialUserInfo.getName())
                .role(UserRole.USER)
                .email(socialUserInfo.getEmail())
                .build();

        User savedUser = userRepository.save(newUser);
        log.info("신규 사용자 생성 완료 - username: {}", savedUser.getUsername());

        // 소셜 계정 정보 저장
        SocialAccount socialAccount = SocialAccount.builder()
                .user(savedUser)
                .provider(provider)
                .socialId(socialUserInfo.getId())
                .email(socialUserInfo.getEmail())
                .socialUsername(socialUserInfo.getName())
                .build();

        socialAccountRepository.save(socialAccount);
        log.info("{} 소셜 계정 생성 완료 - username: {}", provider, savedUser.getUsername());

        return savedUser;
    }

    private String generateUniqueUsername(String baseUsername) {
        String username = baseUsername;
        int counter = 1;
        
        while (userRepository.existsByUsername(username)) {
            username = baseUsername + "_" + counter;
            counter++;
        }
        
        return username;
    }
}