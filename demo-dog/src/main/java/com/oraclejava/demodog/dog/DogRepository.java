package com.oraclejava.demodog.dog;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DogRepository extends JpaRepository<DogEntity, Long> {

	List<DogEntity> findAllByOrderByIdAsc();

	/** 동시 요청에서도 누락되지 않도록 DB에서 원자적으로 조회수를 1 올린다. 삭제된 행은 올리지 않는다. */
	@Modifying(clearAutomatically = true)
	@Query("update DogEntity d set d.readcount = d.readcount + 1 where d.id = :id and d.deletedAt is null")
	int increaseReadcount(@Param("id") Long id);

	/** soft delete된 행을 되살린다. 엔티티 필터가 삭제 행을 숨기므로 네이티브 쿼리를 쓴다. 되살린 행 수를 반환한다. */
	@Modifying(clearAutomatically = true)
	@Query(value = "update dogs set deleted_at = null where id = :id and deleted_at is not null",
			nativeQuery = true)
	int restore(@Param("id") Long id);

	/** soft delete된 행만 최근 삭제 순으로 읽는다. 엔티티 필터가 삭제 행을 숨기므로 네이티브 쿼리를 쓴다. */
	@Query(value = "select id as id, kind as kind, price as price, image as image, deleted_at as deletedAt "
			+ "from dogs where deleted_at is not null order by deleted_at desc, id desc", nativeQuery = true)
	List<DeletedDogRow> findDeleted();
}
