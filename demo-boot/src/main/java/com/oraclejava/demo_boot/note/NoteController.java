package com.oraclejava.demo_boot.note;

import java.net.URI;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/notes")
public class NoteController {

	private final NoteService noteService;

	public NoteController(NoteService noteService) {
		this.noteService = noteService;
	}

	@GetMapping
	public List<Note> list() {
		return noteService.findAll();
	}

	@GetMapping("/{id}")
	public Note get(@PathVariable Long id) {
		return noteService.findById(id).orElseThrow(() -> notFound(id));
	}

	@PostMapping
	public ResponseEntity<Note> create(@RequestBody NoteRequest request) {
		validate(request);
		Note created = noteService.create(request);
		return ResponseEntity.created(URI.create("/api/notes/" + created.id())).body(created);
	}

	@PutMapping("/{id}")
	public Note update(@PathVariable Long id, @RequestBody NoteRequest request) {
		validate(request);
		return noteService.update(id, request).orElseThrow(() -> notFound(id));
	}

	@PatchMapping("/{id}")
	public Note patch(@PathVariable Long id, @RequestBody NoteRequest request) {
		if (request.title() != null && request.title().isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "title must not be blank");
		}
		return noteService.patch(id, request).orElseThrow(() -> notFound(id));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable Long id) {
		if (!noteService.delete(id)) {
			throw notFound(id);
		}
		return ResponseEntity.noContent().build();
	}

	private static void validate(NoteRequest request) {
		if (request == null || request.title() == null || request.title().isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "title is required");
		}
	}

	private static ResponseStatusException notFound(Long id) {
		return new ResponseStatusException(HttpStatus.NOT_FOUND, "Note not found: " + id);
	}
}
