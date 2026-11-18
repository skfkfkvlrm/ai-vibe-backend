package com.oraclejava.demo_boot.note;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/** notes 테이블에 매핑되는 JPA 엔티티. API 응답은 {@link Note} 레코드로 변환해 반환한다. */
@Entity
@Table(name = "notes")
public class NoteEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 200)
	private String title;

	@Lob
	@Column(columnDefinition = "TEXT")
	private String content;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected NoteEntity() {
	}

	public NoteEntity(String title, String content) {
		this.title = title;
		this.content = content;
	}

	@PrePersist
	void onCreate() {
		Instant now = Instant.now();
		this.createdAt = now;
		this.updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		this.updatedAt = Instant.now();
	}

	public void changeTitle(String title) {
		this.title = title;
	}

	public void changeContent(String content) {
		this.content = content;
	}

	public Note toNote() {
		return new Note(id, title, content, createdAt, updatedAt);
	}

	public Long getId() {
		return id;
	}
}
