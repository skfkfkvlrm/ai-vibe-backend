package com.oraclejava.demodog.image;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/images")
public class ImageController {

	/** 이미지 받아오기 요청 본문. */
	public record FetchRequest(String kind) {
	}

	/** 저장된 이미지 경로 응답. source는 자동 수집일 때만 채워진다. */
	public record ImageResponse(String image, String source) {
	}

	private final ImageStorage storage;
	private final NamuImagePipeline pipeline;

	public ImageController(ImageStorage storage, NamuImagePipeline pipeline) {
		this.storage = storage;
		this.pipeline = pipeline;
	}

	/** 직접 첨부한 이미지를 저장한다 (multipart, 필드명 file). */
	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ImageResponse upload(@RequestParam("file") MultipartFile file) {
		try {
			return new ImageResponse(storage.save(file.getBytes()), null);
		} catch (IOException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cannot read uploaded file");
		}
	}

	/** 품종 이름으로 나무위키에서 이미지를 받아와 저장한다. */
	@PostMapping(path = "/fetch", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ImageResponse fetch(@RequestBody FetchRequest request) {
		NamuImagePipeline.Result result = pipeline.fetch(request == null ? null : request.kind());
		return new ImageResponse(result.image(), result.source());
	}
}
