package com.oraclejava.demodog.dog;

import java.net.URI;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 개 추가 화면용 엔드포인트. JSON 본문만 받는다. */
@RestController
@RequestMapping("/api/add")
public class DogAddController {

	private final DogService dogService;

	public DogAddController(DogService dogService) {
		this.dogService = dogService;
	}

	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Dog> add(@RequestBody DogRequest request) {
		DogController.validate(request);
		Dog created = dogService.create(request);
		return ResponseEntity.created(URI.create("/api/dogs/" + created.id())).body(created);
	}
}
