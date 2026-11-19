package com.oraclejava.pc_mall.member;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** mall_member 테이블에 매핑되는 JPA 엔티티. API 응답은 {@link MemberResponse}로 변환해 반환한다. */
@Entity
@Table(name = "mall_member", uniqueConstraints = {
		@UniqueConstraint(name = "uk_mall_member_login_id", columnNames = "login_id"),
		@UniqueConstraint(name = "uk_mall_member_email", columnNames = "email") })
public class MemberEntity {

	public static final String ROLE_USER = "USER";
	/** 관리자. 가입으로는 만들 수 없고 DB에서 role을 직접 승격한다. */
	public static final String ROLE_ADMIN = "ADMIN";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "member_id")
	private Long memberId;

	@Column(name = "login_id", nullable = false, length = 50)
	private String loginId;

	/** 비밀번호 (BCrypt 해시) */
	@Column(name = "password", nullable = false, length = 100)
	private String password;

	@Column(name = "name", nullable = false, length = 100)
	private String name;

	@Column(name = "email", nullable = false, length = 200)
	private String email;

	@Column(name = "role", nullable = false, length = 20)
	private String role;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	/** 밴 시작 시각. null이면 밴이 아니다. */
	@Column(name = "banned_at")
	private LocalDateTime bannedAt;

	/** 밴 해제 시각. bannedAt이 있고 이 값이 null이면 영구 밴이다. 지나면 자동으로 해제된 것으로 본다. */
	@Column(name = "banned_until")
	private LocalDateTime bannedUntil;

	@Column(name = "ban_reason", length = 500)
	private String banReason;

	/** 비밀번호를 바꿀 때 올린다. 토큰의 ver와 다르면 그 토큰은 무효다. */
	@Column(name = "token_version", columnDefinition = "integer not null default 0")
	private int tokenVersion;

	protected MemberEntity() {
	}

	public MemberEntity(String loginId, String passwordHash, String name, String email, LocalDateTime now) {
		this.loginId = loginId;
		this.password = passwordHash;
		this.name = name;
		this.email = email;
		this.role = ROLE_USER;
		this.createdAt = now;
	}

	public MemberResponse toResponse() {
		return new MemberResponse(memberId, loginId, name, email, role, createdAt);
	}

	/** 지금 밴 상태인가. 기간 밴은 해제 시각이 지나면 false(별도 해제 작업 없음). */
	public boolean isBanned(LocalDateTime now) {
		return bannedAt != null && (bannedUntil == null || bannedUntil.isAfter(now));
	}

	/** untilOrNull이 null이면 영구 밴. */
	public void ban(String reason, LocalDateTime now, LocalDateTime untilOrNull) {
		this.bannedAt = now;
		this.bannedUntil = untilOrNull;
		this.banReason = reason;
	}

	/** 밴을 풀고 사유·기간을 지운다. */
	public void unban() {
		this.bannedAt = null;
		this.bannedUntil = null;
		this.banReason = null;
	}

	/** 영구 밴인가. 기간 밴은 false. */
	public boolean isBannedPermanently(LocalDateTime now) {
		return isBanned(now) && bannedUntil == null;
	}

	/** 관리자 화면용 상태: BANNED 또는 ACTIVE(기간 밴이 지났으면 ACTIVE). */
	public String status(LocalDateTime now) {
		return isBanned(now) ? "BANNED" : "ACTIVE";
	}

	/** 역할을 바꾼다. 허용 값(USER·ADMIN) 검증은 호출한 서비스가 한다. */
	public void changeRole(String newRole) {
		this.role = newRole;
	}

	/** 비밀번호를 바꾸고 기존 로그인을 모두 무효화한다. */
	public void changePassword(String passwordHash) {
		this.password = passwordHash;
		this.tokenVersion++;
	}

	/** null인 항목은 바꾸지 않는다. */
	public void updateProfile(String newName, String newEmail) {
		if (newName != null) this.name = newName;
		if (newEmail != null) this.email = newEmail;
	}

	public String getEmail() {
		return email;
	}

	public String getLoginId() {
		return loginId;
	}

	public String getName() {
		return name;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public Long getMemberId() {
		return memberId;
	}

	public String getPassword() {
		return password;
	}

	public String getRole() {
		return role;
	}

	public LocalDateTime getBannedUntil() {
		return bannedUntil;
	}

	public String getBanReason() {
		return banReason;
	}

	public int getTokenVersion() {
		return tokenVersion;
	}
}
