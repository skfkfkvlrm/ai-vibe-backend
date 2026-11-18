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

@Entity
@Table(name = "product")
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
