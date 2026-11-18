package com.oraclejava.demodog.dog;

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
@RequestMapping("/api/dogs")
public class DogController {

	private final DogService dogService;

	public DogController(DogService dogService) {
		this.dogService = dogService;
	}

	@GetMapping
	public List<Dog> list() {
		return dogService.findAll();
	}

	/** 단건 조회. 호출될 때마다 readcount가 1 증가한다. */
	@GetMapping("/{id}")
	public Dog get(@PathVariable Long id) {
		return dogService.readAndCount(id).orElseThrow(() -> notFound(id));
	}

	@PostMapping
	public ResponseEntity<Dog> create(@RequestBody DogRequest request) {
		validate(request);
		Dog created = dogService.create(request);
		return ResponseEntity.created(URI.create("/api/dogs/" + created.id())).body(created);
	}

	@PutMapping("/{id}")
	public Dog update(@PathVariable Long id, @RequestBody DogRequest request) {
		validate(request);
		return dogService.update(id, request).orElseThrow(() -> notFound(id));
	}

	@PatchMapping("/{id}")
	public Dog patch(@PathVariable Long id, @RequestBody DogRequest request) {
		if (request.kind() != null && request.kind().isBlank()) {
			throw badRequest("kind must not be blank");
		}
		checkNumbers(request);
		return dogService.patch(id, request).orElseThrow(() -> notFound(id));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable Long id) {
		if (!dogService.delete(id)) {
			throw notFound(id);
		}
		return ResponseEntity.noContent().build();
	}

	static void validate(DogRequest request) {
		if (request == null || request.kind() == null || request.kind().isBlank()) {
			throw badRequest("kind is required");
		}
		checkNumbers(request);
	}

	private static void checkNumbers(DogRequest r) {
		if (r.price() != null && r.price() < 0) throw badRequest("price must not be negative");
		if (r.height() != null && r.height() < 0) throw badRequest("height must not be negative");
		if (r.weight() != null && r.weight() < 0) throw badRequest("weight must not be negative");
	}

	private static ResponseStatusException badRequest(String message) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
	}

	private static ResponseStatusException notFound(Long id) {
		return new ResponseStatusException(HttpStatus.NOT_FOUND, "Dog not found: " + id);
	}
}
