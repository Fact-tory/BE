package com.commonground.be.global.domain.audit;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import java.time.LocalDateTime;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class SoftDeleteTimeStamp {

	@CreatedDate
	@Column(updatable = false)
	private LocalDateTime createdAt;

	@LastModifiedDate
	private LocalDateTime updatedAt;

	// 수동으로 관리하는 소프트 삭제 필드
	@Column(name = "deleted_at")
	private LocalDateTime deletedAt;

	// 편의 메서드들
	public boolean isDeleted() {
		return deletedAt != null;
	}

	public boolean isActive() {
		return deletedAt == null;
	}

	// 소프트 삭제 실행 (더 명확한 메서드명)
	public void softDelete() {
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