package com.commonground.be.domain.media.entity;

import com.commonground.be.domain.news.enums.CrawlingPlatformEnum;
import com.commonground.be.domain.media.enums.PoliticalBiasEnum;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "media_outlets")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MediaOutlet {

    @Id
    private String id;

    @NotBlank
    @Indexed
    private String name;

    @NotBlank
    @Indexed(unique = true)
    private String domain;

    private String website;

    @Indexed
    @Builder.Default
    private PoliticalBiasEnum politicalBias = PoliticalBiasEnum.NEUTRAL;

    private CrawlingPlatformEnum crawlingPlatform;

    private String crawlingUrl;

    private Map<String, Object> crawlingSettings;

    @Builder.Default
    private Boolean isActive = true;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    private LocalDateTime deletedAt;
    
    // 도메인 메서드
    public void assignId(String id) {
        this.id = id;
    }
}
