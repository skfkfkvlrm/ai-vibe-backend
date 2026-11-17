package com.oraclejava.pc_mall.product;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ProductRequest(String name, int price, String kind, String text,
		@JsonProperty("pic_s") String picS, @JsonProperty("pic_l") String picL) {
}
