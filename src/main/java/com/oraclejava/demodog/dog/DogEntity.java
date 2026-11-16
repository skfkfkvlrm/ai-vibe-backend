package com.oraclejava.demodog.dog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

/** dogs 테이블에 매핑되는 JPA 엔티티. API 응답은 {@link Dog} 레코드로 변환해 반환한다. */
@Entity
@Table(name = "dogs")
public class DogEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** 품종 */
	@Column(nullable = false, length = 100)
	private String kind;

	private Integer price;

	/** 이미지 경로 또는 URL */
	@Column(length = 500)
	private String image;

	/** 원산지 */
	@Column(length = 100)
	private String country;

	private Double height;

	private Double weight;

	/** 설명 */
	@Lob
	@Column(columnDefinition = "TEXT")
	private String content;

	/** 조회수 */
	@Column(nullable = false)
	private long readcount;

	protected DogEntity() {
	}

	public DogEntity(DogRequest request) {
		apply(request);
	}

	/** 요청의 모든 필드로 덮어쓴다 (PUT). */
	public void apply(DogRequest r) {
		this.kind = r.kind();
		this.price = r.price();
		this.image = r.image();
		this.country = r.country();
		this.height = r.height();
		this.weight = r.weight();
		this.content = r.content();
	}

	/** 요청에서 null이 아닌 필드만 반영한다 (PATCH). */
	public void applyNonNull(DogRequest r) {
		if (r.kind() != null) this.kind = r.kind();
		if (r.price() != null) this.price = r.price();
		if (r.image() != null) this.image = r.image();
		if (r.country() != null) this.country = r.country();
		if (r.height() != null) this.height = r.height();
		if (r.weight() != null) this.weight = r.weight();
		if (r.content() != null) this.content = r.content();
	}

	public Dog toDog() {
		return new Dog(id, kind, price, image, country, height, weight, content, readcount);
	}

	public Long getId() {
		return id;
	}
}
