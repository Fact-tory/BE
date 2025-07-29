package com.commonground.be.global.stamps;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import java.time.LocalDateTime;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class TimeStamp {

    @CreatedDate
    @Column(updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column
    @Temporal(TemporalType.TIMESTAMP)
    private LocalDateTime updatedAt;

    // 편의 메서드들
    public long getDaysSinceCreated() {
        return java.time.temporal.ChronoUnit.DAYS.between(createdAt, LocalDateTime.now());
    }

    public long getDaysSinceUpdated() {
        return java.time.temporal.ChronoUnit.DAYS.between(updatedAt, LocalDateTime.now());
    }

    public boolean isRecentlyCreated(int days) {
        return getDaysSinceCreated() <= days;
    }

    public boolean isRecentlyUpdated(int days) {
        return getDaysSinceUpdated() <= days;
    }
}