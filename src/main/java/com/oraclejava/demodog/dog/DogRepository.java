package com.oraclejava.demodog.dog;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DogRepository extends JpaRepository<DogEntity, Long> {

	List<DogEntity> findAllByOrderByIdAsc();

	/** 동시 요청에서도 누락되지 않도록 DB에서 원자적으로 조회수를 1 올린다. */
	@Modifying(clearAutomatically = true)
	@Query("update DogEntity d set d.readcount = d.readcount + 1 where d.id = :id")
	int increaseReadcount(@Param("id") Long id);
}
