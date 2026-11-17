package com.oraclejava.pc_mall.product;

import java.io.IOException;
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

	@Transactional
	public ProductResponse create(String name, int price, String kind, String text,
			MultipartFile picS, MultipartFile picL) {
		Product product = new Product(name, price, kind, text);
		product.changePics(toBytes(picS), toBytes(picL));
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

	@Transactional
	public ProductResponse update(Long id, String name, int price, String kind, String text,
			MultipartFile picS, MultipartFile picL) {
		Product product = get(id);
		product.update(name, price, kind, text);
		product.changePics(toBytes(picS), toBytes(picL));
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
