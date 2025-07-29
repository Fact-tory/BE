package com.commonground.be.domain.social.entity;

import com.commonground.be.domain.user.entity.User;
import com.commonground.be.global.stamps.TimeStamp;
import com.commonground.be.global.security.EmailEncryptionConverter;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
    name = "social_account",
    indexes = {
        @Index(name = "idx_provider_social_id", columnList = "provider, socialId"),
        @Index(name = "idx_user_id", columnList = "user_id"),
        @Index(name = "idx_email", columnList = "email")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_provider_social_id", columnNames = {"provider", "socialId"})
    }
)
public class SocialAccount extends TimeStamp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private SocialProvider provider;

    @Column(nullable = false)
    private String socialId; // 소셜 서비스에서 제공하는 사용자 ID

    @Column(nullable = false)
    @Convert(converter = EmailEncryptionConverter.class)
    private String email;

    @Column
    private String socialUsername; // 소셜에서 제공하는 사용자명

    @Builder
    public SocialAccount(User user, SocialProvider provider, String socialId, 
                        String email, String socialUsername) {
        this.user = user;
        this.provider = provider;
        this.socialId = socialId;
        this.email = email;
        this.socialUsername = socialUsername;
    }

    // 이메일 동기화 메서드
    public void updateEmail(String newEmail) {
        this.email = newEmail;
    }

    public enum SocialProvider {
        KAKAO, GOOGLE, NAVER, FACEBOOK
    }
}