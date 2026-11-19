package com.oraclejava.demoboard.security;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/** 비회원 쓰기 1건의 기록(IP, 종류, 시각). 제한 판정에만 쓰며 오래된 행은 읽을 때 무시한다. */
@Entity
@Table(name = "write_rate_limit", indexes = @Index(name = "idx_write_rate_limit", columnList = "ip, kind, created_at"))
public class WriteRateLimitEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "limit_id")
	private Long limitId;

	@Column(name = "ip", nullable = false, length = 64)
	private String ip;

	@Column(name = "kind", nullable = false, length = 10)
	private String kind;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	protected WriteRateLimitEntity() {
	}

	public WriteRateLimitEntity(String ip, String kind, LocalDateTime createdAt) {
		this.ip = ip;
		this.kind = kind;
		this.createdAt = createdAt;
	}

	public LocalDateTime getCreatedAt() { return createdAt; }
}
