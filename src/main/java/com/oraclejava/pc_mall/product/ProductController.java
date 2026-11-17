package com.oraclejava.pc_mall.product;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URLConnection;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/products")
public class ProductController {

	private final ProductService service;

	public ProductController(ProductService service) {
		this.service = service;
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<ProductResponse> create(@RequestParam String name, @RequestParam int price,
			@RequestParam String kind, @RequestParam(required = false) String text,
			@RequestParam(name = "pic_s", required = false) MultipartFile picS,
			@RequestParam(name = "pic_l", required = false) MultipartFile picL) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(service.create(name, price, kind, text, picS, picL));
	}

	@GetMapping
	public List<ProductResponse> list() {
		return service.findAll();
	}

	@GetMapping("/{id}")
	public ProductResponse get(@PathVariable Long id) {
		return service.find(id);
	}

	@GetMapping("/{id}/pic-s")
	public ResponseEntity<byte[]> picS(@PathVariable Long id) {
		return image(service.picS(id));
	}

	@GetMapping("/{id}/pic-l")
	public ResponseEntity<byte[]> picL(@PathVariable Long id) {
		return image(service.picL(id));
	}

	@PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ProductResponse update(@PathVariable Long id, @RequestParam String name, @RequestParam int price,
			@RequestParam String kind, @RequestParam(required = false) String text,
			@RequestParam(name = "pic_s", required = false) MultipartFile picS,
			@RequestParam(name = "pic_l", required = false) MultipartFile picL) {
		return service.update(id, name, price, kind, text, picS, picL);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable Long id) {
		service.delete(id);
		return ResponseEntity.noContent().build();
	}

	private ResponseEntity<byte[]> image(byte[] bytes) {
		String type = null;
		try {
			type = URLConnection.guessContentTypeFromStream(new ByteArrayInputStream(bytes));
		} catch (IOException ignored) {
			// 판별 실패 시 octet-stream 사용
		}
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(type != null ? type : MediaType.APPLICATION_OCTET_STREAM_VALUE))
				.body(bytes);
	}
}
