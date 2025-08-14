package com.commonground.be.domain.media.entity;

import java.time.LocalDateTime;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.NotBlank;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "media_outlets")
@Data
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
    private PoliticalBiasEnum politicalBias = PoliticalBiasEnum.NEUTRAL;

    private CrawlingPlatformEnum crawlingPlatform;

    private String crawlingUrl;

    private Map<String, Object> crawlingSettings;

    private Boolean isActive = true;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    private LocalDateTime deletedAt;
}
