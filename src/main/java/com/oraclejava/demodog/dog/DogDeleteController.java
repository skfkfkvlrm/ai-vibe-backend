package com.oraclejava.demodog.dog;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** 개 삭제 화면용 엔드포인트. 항상 soft delete다. */
@RestController
@RequestMapping("/api/delete")
public class DogDeleteController {

	private final DogService dogService;

	public DogDeleteController(DogService dogService) {
		this.dogService = dogService;
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable Long id) {
		if (!dogService.delete(id)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Dog not found: " + id);
		}
		return ResponseEntity.noContent().build();
	}
}
