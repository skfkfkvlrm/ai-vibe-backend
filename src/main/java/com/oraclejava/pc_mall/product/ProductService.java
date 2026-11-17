package com.oraclejava.pc_mall.product;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URLConnection;
import java.util.Base64;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProductService {

	private final ProductRepository repository;

	public ProductService(ProductRepository repository) {
		this.repository = repository;
	}

	public ProductResponse create(String name, int price, String kind, String text,
			MultipartFile picS, MultipartFile picL) {
		return create(name, price, kind, text, toBytes(picS), toBytes(picL));
	}

	public ProductResponse create(ProductRequest req) {
		return create(req.name(), req.price(), req.kind(), req.text(), decode(req.picS()), decode(req.picL()));
	}

	@Transactional
	public ProductResponse create(String name, int price, String kind, String text, byte[] picS, byte[] picL) {
		requireFields(name, kind);
		Product product = new Product(name, price, kind, text);
		product.changePics(picS, picL);
		return ProductResponse.from(repository.save(product));
	}

	@Transactional(readOnly = true)
	public List<ProductResponse> findAll() {
		return repository.findAll().stream().map(ProductResponse::from).toList();
	}

	@Transactional(readOnly = true)
	public ProductResponse find(Long id) {
		return ProductResponse.from(get(id));
	}

	@Transactional(readOnly = true)
	public byte[] picS(Long id) {
		return requirePic(get(id).getPicS());
	}

	@Transactional(readOnly = true)
	public byte[] picL(Long id) {
		return requirePic(get(id).getPicL());
	}

	public ProductResponse update(Long id, String name, int price, String kind, String text,
			MultipartFile picS, MultipartFile picL) {
		return update(id, name, price, kind, text, toBytes(picS), toBytes(picL));
	}

	public ProductResponse update(Long id, ProductRequest req) {
		return update(id, req.name(), req.price(), req.kind(), req.text(), decode(req.picS()), decode(req.picL()));
	}

	@Transactional
	public ProductResponse update(Long id, String name, int price, String kind, String text, byte[] picS,
			byte[] picL) {
		requireFields(name, kind);
		Product product = get(id);
		product.update(name, price, kind, text);
		product.changePics(picS, picL);
		return ProductResponse.from(product);
	}

	@Transactional
	public void delete(Long id) {
		repository.delete(get(id));
	}

	private Product get(Long id) {
		return repository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "상품을 찾을 수 없습니다: " + id));
	}

	private byte[] requirePic(byte[] pic) {
		if (pic == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "이미지가 없습니다");
		}
		return pic;
	}

	private void requireFields(String name, String kind) {
		if (name == null || name.isBlank() || kind == null || kind.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name, kind는 필수입니다");
		}
	}

	private byte[] decode(String base64) {
		if (base64 == null || base64.isBlank()) {
			return null;
		}
		String data = base64.startsWith("data:") ? base64.substring(base64.indexOf(',') + 1) : base64;
		byte[] bytes;
		try {
			bytes = Base64.getMimeDecoder().decode(data.trim());
		} catch (IllegalArgumentException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "올바른 Base64 형식이 아닙니다");
		}
		String type = null;
		try {
			type = URLConnection.guessContentTypeFromStream(new ByteArrayInputStream(bytes));
		} catch (IOException ignored) {
			// 판별 실패는 아래에서 400 처리
		}
		if (type == null || !type.startsWith("image/")) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이미지 데이터가 아닙니다 (PNG/JPEG/GIF/BMP만 지원)");
		}
		return bytes;
	}

	private byte[] toBytes(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			return null;
		}
		String type = file.getContentType();
		if (type == null || !type.startsWith("image/")) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이미지 파일만 업로드할 수 있습니다");
		}
		try {
			return file.getBytes();
		} catch (IOException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이미지를 읽을 수 없습니다");
		}
	}
}
