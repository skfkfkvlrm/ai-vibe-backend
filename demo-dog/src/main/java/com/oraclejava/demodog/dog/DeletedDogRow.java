package com.oraclejava.demodog.dog;

import java.time.LocalDateTime;

/** {@link DogRepository#findDeleted()} 네이티브 쿼리 결과 프로젝션. */
public interface DeletedDogRow {

	Long getId();

	String getKind();

	Integer getPrice();

	String getImage();

	LocalDateTime getDeletedAt();
}
