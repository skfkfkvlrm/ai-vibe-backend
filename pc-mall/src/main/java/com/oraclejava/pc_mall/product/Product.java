package com.oraclejava.pc_mall.product;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

// 삭제는 soft delete: DELETE 대신 deleted_at을 채우고, 조회에서는 deleted_at이 있는 행을 제외한다.
@Entity
@Table(name = "product")
@SQLDelete(sql = "UPDATE product SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class Product {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private int price;

	@Column(nullable = false, length = 50)
	private String kind;

	@Column(name = "text", columnDefinition = "TEXT")
	private String text;

	@Lob
	@Basic(fetch = FetchType.LAZY)
	@Column(name = "pic_s", columnDefinition = "MEDIUMBLOB")
	private byte[] picS;

	@Lob
	@Basic(fetch = FetchType.LAZY)
	@Column(name = "pic_l", columnDefinition = "MEDIUMBLOB")
	private byte[] picL;

	@Column(name = "deleted_at")
	private LocalDateTime deletedAt;

	protected Product() {
	}

	public Product(String name, int price, String kind, String text) {
		update(name, price, kind, text);
	}

	public void update(String name, int price, String kind, String text) {
		this.name = name;
		this.price = price;
		this.kind = kind;
		this.text = text;
	}

	public void changePics(byte[] picS, byte[] picL) {
		if (picS != null) {
			this.picS = picS;
		}
		if (picL != null) {
			this.picL = picL;
		}
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public int getPrice() {
		return price;
	}

	public String getKind() {
		return kind;
	}

	public String getText() {
		return text;
	}

	public byte[] getPicS() {
		return picS;
	}

	public byte[] getPicL() {
		return picL;
	}
}
