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

	/** 권한. 가입은 항상 USER이며 관리자 부여는 이후 단계에서 다룬다. */
	@Column(name = "role", nullable = false, length = 20)
	private String role;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

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

	public String getRole() {
		return role;
	}
}
