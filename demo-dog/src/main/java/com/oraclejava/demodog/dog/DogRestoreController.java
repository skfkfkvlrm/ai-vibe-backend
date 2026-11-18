package com.oraclejava.demodog.dog;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** soft delete된 개를 되살린다. 화면은 없고 API로만 호출한다. */
@RestController
@RequestMapping("/api/restore")
public class DogRestoreController {

	private final DogService dogService;

	public DogRestoreController(DogService dogService) {
		this.dogService = dogService;
	}

	@PostMapping("/{id}")
	public Dog restore(@PathVariable Long id) {
		return dogService.restore(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
						"Deleted dog not found: " + id));
	}
}
