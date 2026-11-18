package com.oraclejava.demodog.dog;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** 개 수정 화면용 엔드포인트. 전체 교체(PUT)이며 본문에 없는 선택 항목은 null로 저장된다. */
@RestController
@RequestMapping("/api/update")
public class DogUpdateController {

	private final DogService dogService;

	public DogUpdateController(DogService dogService) {
		this.dogService = dogService;
	}

	@PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
	public Dog update(@PathVariable Long id, @RequestBody DogRequest request) {
		DogController.validate(request);
		return dogService.update(id, request)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dog not found: " + id));
	}
}
