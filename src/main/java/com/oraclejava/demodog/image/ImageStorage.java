package com.oraclejava.demodog.image;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** 업로드 폴더에 이미지를 저장한다. 파일명은 서버가 만든 UUID이며 형식은 파일 앞 바이트로 판별한다. */
@Component
public class ImageStorage {

	static final int MAX_BYTES = 3 * 1024 * 1024;

	private final Path root;

	public ImageStorage(@Value("${app.upload-dir:uploads}") String uploadDir) {
		this.root = Path.of(uploadDir).toAbsolutePath().normalize();
		try {
			Files.createDirectories(root);
		} catch (IOException e) {
			throw new IllegalStateException("cannot create upload dir: " + root, e);
		}
	}

	public Path root() {
		return root;
	}

	/** 저장하고 API에 노출할 경로(예: uploads/xxxx.webp)를 반환한다. */
	public String save(byte[] data) {
		if (data == null || data.length == 0) {
			throw bad("image is empty");
		}
		if (data.length > MAX_BYTES) {
			throw bad("image is too large (max 3MB)");
		}
		String ext = detectExtension(data);
		if (ext == null) {
			throw bad("only png, jpeg, webp images are allowed");
		}
		String name = UUID.randomUUID() + "." + ext;
		try {
			Files.write(root.resolve(name), data, StandardOpenOption.CREATE_NEW);
		} catch (IOException e) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "failed to store image");
		}
		return "uploads/" + name;
	}

	/** 확장자나 Content-Type이 아니라 실제 바이트(매직 넘버)로 형식을 판별한다. 지원하지 않으면 null. */
	static String detectExtension(byte[] d) {
		if (d.length >= 8 && (d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G') {
			return "png";
		}
		if (d.length >= 3 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF) {
			return "jpg";
		}
		if (d.length >= 12 && d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F'
				&& d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P') {
			return "webp";
		}
		return null;
	}

	private static ResponseStatusException bad(String message) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
	}
}
