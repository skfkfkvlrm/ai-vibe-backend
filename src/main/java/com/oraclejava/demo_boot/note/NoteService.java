package com.oraclejava.demo_boot.note;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Spring Data JPA 기반 노트 서비스. 데이터는 MySQL notes 테이블에 저장된다. */
@Service
@Transactional(readOnly = true)
public class NoteService {

	private final NoteRepository noteRepository;

	public NoteService(NoteRepository noteRepository) {
		this.noteRepository = noteRepository;
	}

	public List<Note> findAll() {
		return noteRepository.findAllByOrderByCreatedAtDescIdDesc().stream()
				.map(NoteEntity::toNote)
				.toList();
	}

	public Optional<Note> findById(Long id) {
		return noteRepository.findById(id).map(NoteEntity::toNote);
	}

	@Transactional
	public Note create(NoteRequest request) {
		return noteRepository.saveAndFlush(new NoteEntity(request.title(), request.content())).toNote();
	}

	@Transactional
	public Optional<Note> update(Long id, NoteRequest request) {
		return noteRepository.findById(id).map(entity -> {
			entity.changeTitle(request.title());
			entity.changeContent(request.content());
			return noteRepository.saveAndFlush(entity).toNote();
		});
	}

	/** 요청에서 null이 아닌 필드만 반영한다. */
	@Transactional
	public Optional<Note> patch(Long id, NoteRequest request) {
		return noteRepository.findById(id).map(entity -> {
			if (request.title() != null) {
				entity.changeTitle(request.title());
			}
			if (request.content() != null) {
				entity.changeContent(request.content());
			}
			return noteRepository.saveAndFlush(entity).toNote();
		});
	}

	@Transactional
	public boolean delete(Long id) {
		if (!noteRepository.existsById(id)) {
			return false;
		}
		noteRepository.deleteById(id);
		return true;
	}
}
