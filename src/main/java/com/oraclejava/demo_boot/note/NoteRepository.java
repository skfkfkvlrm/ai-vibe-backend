package com.oraclejava.demo_boot.note;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NoteRepository extends JpaRepository<NoteEntity, Long> {

	List<NoteEntity> findAllByOrderByCreatedAtDescIdDesc();
}
