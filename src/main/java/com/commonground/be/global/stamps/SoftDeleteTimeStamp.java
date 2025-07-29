package com.commonground.be.global.stamps;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import java.time.LocalDateTime;
import lombok.Getter;
import org.hibernate.annotations.SoftDelete;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@SoftDelete(columnName = "deleted_at") // 엔티티 레벨에서 적용
public abstract class SoftDeleteTimeStamp {

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    // @SoftDelete에 의해 자동 관리
    private LocalDateTime deletedAt;

    // 편의 메서드들
    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean isActive() {
        return deletedAt == null;
    }

    public void markAsDeleted() {
        this.deletedAt = LocalDateTime.now();
    }

    public void restore() {
        this.deletedAt = null;
    }

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