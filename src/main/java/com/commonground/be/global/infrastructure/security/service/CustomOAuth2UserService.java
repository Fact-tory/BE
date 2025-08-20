package com.commonground.be.global.infrastructure.security.service;

import java.util.Collections;
import java.util.Map;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.commonground.be.domain.user.entity.User;
import com.commonground.be.domain.user.repository.UserRepository;
import com.commonground.be.domain.user.utils.UserRole;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * OAuth2 사용자 정보를 처리하는 커스텀 서비스
 * OAuth2 인증 후 사용자 정보를 DB에 저장하고 UserDetails로 변환
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final UserRepository userRepository;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        log.info("🔐 OAuth2 사용자 정보 로드 시작");
        
        // 기본 OAuth2UserService를 통해 사용자 정보 가져오기
        OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate = new DefaultOAuth2UserService();
        OAuth2User oauth2User = delegate.loadUser(userRequest);
        
        // OAuth2 제공자 정보
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        String userNameAttributeName = userRequest.getClientRegistration()
                .getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName();
        
        log.info("OAuth2 제공자: {}, 사용자 식별자: {}", registrationId, userNameAttributeName);
        
        // 제공자별 사용자 정보 추출
        OAuthAttributes attributes = OAuthAttributes.of(registrationId, userNameAttributeName, oauth2User.getAttributes());
        
        // 사용자 생성 또는 업데이트
        User user = saveOrUpdate(attributes);
        
        log.info("✅ OAuth2 사용자 처리 완료 - userId: {}, email: {}", user.getId(), user.getEmail());
        
        // OAuth2User 반환 (Spring Security가 인식할 수 있는 형태)
        return new DefaultOAuth2User(
                Collections.singleton(new SimpleGrantedAuthority("ROLE_" + user.getUserRole().name())),
                attributes.getAttributes(),
                attributes.getNameAttributeKey()
        );
    }

    @Transactional
    protected User saveOrUpdate(OAuthAttributes attributes) {
        // 이메일로 기존 사용자 조회
        User user = userRepository.findByEmail(attributes.getEmail())
                .map(entity -> {
                    log.info("📝 기존 사용자 정보 업데이트 - email: {}", attributes.getEmail());
                    // 필요한 경우 사용자 정보 업데이트
                    return entity;
                })
                .orElse(attributes.toEntity());

        return userRepository.save(user);
    }

    /**
     * OAuth2 제공자별 사용자 정보 매핑 클래스
     */
    public static class OAuthAttributes {
        private final Map<String, Object> attributes;
        private final String nameAttributeKey;
        private final String name;
        private final String email;
        private final String username;

        private OAuthAttributes(Map<String, Object> attributes, String nameAttributeKey, 
                               String name, String email, String username) {
            this.attributes = attributes;
            this.nameAttributeKey = nameAttributeKey;
            this.name = name;
            this.email = email;
            this.username = username;
        }

        public static OAuthAttributes of(String registrationId, String userNameAttributeName,
                                       Map<String, Object> attributes) {
            if ("kakao".equals(registrationId.toLowerCase())) {
                return ofKakao(userNameAttributeName, attributes);
            }
            return ofGoogle(userNameAttributeName, attributes);
        }

        private static OAuthAttributes ofGoogle(String userNameAttributeName, Map<String, Object> attributes) {
            String email = (String) attributes.get("email");
            String name = (String) attributes.get("name");
            // username을 email로 통일 (JWT subject로 사용)
            String username = email != null ? email : "user";

            return new OAuthAttributes(attributes, userNameAttributeName, name, email, username);
        }

        @SuppressWarnings("unchecked")
        private static OAuthAttributes ofKakao(String userNameAttributeName, Map<String, Object> attributes) {
            Map<String, Object> kakaoAccount = (Map<String, Object>) attributes.get("kakao_account");
            Map<String, Object> profile = (Map<String, Object>) kakaoAccount.get("profile");

            String email = (String) kakaoAccount.get("email");
            String name = (String) profile.get("nickname");
            // username을 email로 통일 (JWT subject로 사용)
            String username = email != null ? email : "kakao_user";

            return new OAuthAttributes(attributes, userNameAttributeName, name, email, username);
        }

        public User toEntity() {
            return User.builder()
                    .username(username)
                    .name(name)
                    .email(email)
                    .role(UserRole.USER)
                    .build();
        }

        // Getters
        public Map<String, Object> getAttributes() { return attributes; }
        public String getNameAttributeKey() { return nameAttributeKey; }
        public String getName() { return name; }
        public String getEmail() { return email; }
        public String getUsername() { return username; }
    }
}
