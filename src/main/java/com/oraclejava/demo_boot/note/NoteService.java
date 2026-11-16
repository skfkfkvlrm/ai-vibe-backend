package com.oraclejava.demo_boot.note;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Service;

/** 인메모리 노트 저장소. 애플리케이션 재시작 시 데이터는 사라진다. */
@Service
public class NoteService {

	private final Map<Long, Note> notes = new ConcurrentHashMap<>();
	private final AtomicLong sequence = new AtomicLong();

	public List<Note> findAll() {
		return notes.values().stream()
				.sorted(Comparator.comparing(Note::id))
				.toList();
	}

	public Optional<Note> findById(Long id) {
		return Optional.ofNullable(notes.get(id));
	}

	public Note create(NoteRequest request) {
		Long id = sequence.incrementAndGet();
		Instant now = Instant.now();
		Note note = new Note(id, request.title(), request.content(), now, now);
		notes.put(id, note);
		return note;
	}

	public Optional<Note> update(Long id, NoteRequest request) {
		return Optional.ofNullable(notes.computeIfPresent(id, (key, existing) ->
				new Note(key, request.title(), request.content(), existing.createdAt(), Instant.now())));
	}

	/** 요청에서 null이 아닌 필드만 반영한다. */
	public Optional<Note> patch(Long id, NoteRequest request) {
		return Optional.ofNullable(notes.computeIfPresent(id, (key, existing) -> new Note(
				key,
				request.title() != null ? request.title() : existing.title(),
				request.content() != null ? request.content() : existing.content(),
				existing.createdAt(),
				Instant.now())));
	}

	public boolean delete(Long id) {
		return notes.remove(id) != null;
	}
}
