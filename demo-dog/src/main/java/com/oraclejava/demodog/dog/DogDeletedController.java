package com.oraclejava.demodog.dog;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 복구 화면용: soft delete된 개 목록을 최근 삭제 순으로 조회한다. 조회 전용이다. */
@RestController
@RequestMapping("/api/deleted")
public class DogDeletedController {

	private final DogService dogService;

	public DogDeletedController(DogService dogService) {
		this.dogService = dogService;
	}

	@GetMapping
	public List<DeletedDog> list() {
		return dogService.findDeleted();
	}
}
