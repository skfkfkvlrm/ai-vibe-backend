package com.oraclejava.demoboard.member;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** member 테이블에 매핑되는 JPA 엔티티. API 응답은 {@link MemberResponse}로 변환해 반환한다. */
@Entity
@Table(name = "member", uniqueConstraints = {
		@UniqueConstraint(name = "uk_member_login_id", columnNames = "login_id"),
		@UniqueConstraint(name = "uk_member_email", columnNames = "email") })
public class MemberEntity {

	public static final String ROLE_USER = "USER";
	/** 관리자. 가입으로는 만들 수 없고 DB에서 role을 직접 승격한다. */
	public static final String ROLE_ADMIN = "ADMIN";
	/** 탈퇴한 회원의 표시 이름(회원 행과 작성한 글의 작성자명). */
	public static final String WITHDRAWN_NAME = "탈퇴한 회원";
	/** 탈퇴 시 아이디·이메일을 바꾸는 예약 접두사/도메인. 가입에는 쓸 수 없다. */
	public static final String WITHDRAWN_LOGIN_PREFIX = "withdrawn_";
	public static final String WITHDRAWN_EMAIL_DOMAIN = "@withdrawn.invalid";

	public static final String STATUS_ACTIVE = "ACTIVE";
	public static final String STATUS_BANNED = "BANNED";
	public static final String STATUS_WITHDRAWN = "WITHDRAWN";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "member_id")
	private Long memberId;

	/** 로그인 아이디 */
	@Column(name = "login_id", nullable = false, length = 50)
	private String loginId;

	/** 비밀번호 (BCrypt 해시) */
	@Column(name = "password", nullable = false, length = 100)
	private String password;

	@Column(name = "name", nullable = false, length = 100)
	private String name;

	@Column(name = "email", nullable = false, length = 200)
	private String email;

	/** 권한. 가입은 항상 USER이며 관리자는 DB에서 직접 승격한다. */
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

	/** 탈퇴 시각. 탈퇴한 회원은 개인정보가 익명화된 채 행만 남는다. */
	@Column(name = "withdrawn_at")
	private LocalDateTime withdrawnAt;

	/** 비밀번호 변경·초기화·탈퇴 때 올린다. 토큰의 ver와 다르면 그 토큰은 무효다. */
	@Column(name = "token_version", columnDefinition = "integer not null default 0")
	private int tokenVersion;

	/** 관리자가 초기화한 임시 비밀번호 상태. 회원이 비밀번호를 바꾸면 꺼진다. */
	@Column(name = "password_reset_pending", columnDefinition = "boolean not null default false")
	private boolean passwordResetPending;

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
		return new MemberResponse(memberId, loginId, name, email, role, createdAt, passwordResetPending);
	}

	public boolean isWithdrawn() {
		return withdrawnAt != null;
	}

	/** 지금 밴 상태인가. 기간 밴은 해제 시각이 지나면 false(별도 해제 작업 없음). */
	public boolean isBanned(LocalDateTime now) {
		return bannedAt != null && (bannedUntil == null || bannedUntil.isAfter(now));
	}

	public boolean isBannedPermanently(LocalDateTime now) {
		return isBanned(now) && bannedUntil == null;
	}

	public String status(LocalDateTime now) {
		if (isWithdrawn()) return STATUS_WITHDRAWN;
		return isBanned(now) ? STATUS_BANNED : STATUS_ACTIVE;
	}

	/** untilOrNull이 null이면 영구 밴. */
	public void ban(String reason, LocalDateTime now, LocalDateTime untilOrNull) {
		this.bannedAt = now;
		this.bannedUntil = untilOrNull;
		this.banReason = reason;
	}

	public void unban() {
		this.bannedAt = null;
		this.bannedUntil = null;
		this.banReason = null;
	}

	/** 비밀번호를 바꾸고 기존 로그인을 모두 무효화한다. resetPending은 관리자 초기화일 때만 true. */
	public void changePassword(String passwordHash, boolean resetPending) {
		this.password = passwordHash;
		this.passwordResetPending = resetPending;
		this.tokenVersion++;
	}

	public void updateProfile(String newName, String newEmail) {
		if (newName != null) this.name = newName;
		if (newEmail != null) this.email = newEmail;
	}

	/** 탈퇴: 개인정보를 익명화한다. 행(member_id)은 남겨 작성한 글과의 연결을 유지한다. */
	public void withdraw(LocalDateTime now, String randomPasswordHash) {
		this.loginId = WITHDRAWN_LOGIN_PREFIX + memberId;
		this.email = WITHDRAWN_LOGIN_PREFIX + memberId + WITHDRAWN_EMAIL_DOMAIN;
		this.name = WITHDRAWN_NAME;
		this.password = randomPasswordHash;
		this.withdrawnAt = now;
		this.passwordResetPending = false;
		unban();
		this.tokenVersion++;
	}

	public Long getMemberId() {
		return memberId;
	}

	public String getLoginId() {
		return loginId;
	}

	public String getPassword() {
		return password;
	}

	public String getName() {
		return name;
	}

	public String getEmail() {
		return email;
	}

	public String getRole() {
		return role;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getBannedUntil() {
		return bannedUntil;
	}

	public String getBanReason() {
		return banReason;
	}

	public LocalDateTime getWithdrawnAt() {
		return withdrawnAt;
	}

	public int getTokenVersion() {
		return tokenVersion;
	}

	public boolean isPasswordResetPending() {
		return passwordResetPending;
	}
}
