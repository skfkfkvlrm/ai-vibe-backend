package com.oraclejava.pc_mall.product;

public record ProductResponse(Long id, String name, int price, String kind, String text,
		boolean hasPicS, boolean hasPicL) {

	public static ProductResponse from(Product p) {
		return new ProductResponse(p.getId(), p.getName(), p.getPrice(), p.getKind(), p.getText(),
				p.getPicS() != null, p.getPicL() != null);
	}
}
