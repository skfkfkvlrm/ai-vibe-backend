package com.oraclejava.demodog.image;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** POST /api/images 업로드 테스트. 임시 폴더를 쓰고, 파이프라인(외부 호출)은 사용하지 않는다. */
class ImageControllerTests {

	private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};

	@TempDir
	Path tempDir;

	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		ImageStorage storage = new ImageStorage(tempDir.toString());
		mvc = MockMvcBuilders.standaloneSetup(new ImageController(storage, null)).build();
	}

	@Test
	void uploadStoresPngUnderServerGeneratedName() throws Exception {
		mvc.perform(multipart("/api/images")
				.file(new MockMultipartFile("file", "../../evil.png", "image/png", PNG)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.image").value(Matchers.matchesPattern("uploads/[0-9a-f-]{36}\\.png")));
		try (var files = Files.list(tempDir)) {
			Assertions.assertEquals(1, files.count());
		}
	}

	@Test
	void nonImageFileIsRejected() throws Exception {
		mvc.perform(multipart("/api/images")
				.file(new MockMultipartFile("file", "a.png", "image/png", "not an image".getBytes())))
				.andExpect(status().isBadRequest());
		try (var files = Files.list(tempDir)) {
			Assertions.assertEquals(0, files.count());
		}
	}

	@Test
	void oversizedImageIsRejected() throws Exception {
		byte[] big = new byte[ImageStorage.MAX_BYTES + 1];
		System.arraycopy(PNG, 0, big, 0, PNG.length);
		mvc.perform(multipart("/api/images")
				.file(new MockMultipartFile("file", "big.png", "image/png", big)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void detectsFormatsByMagicBytes() {
		Assertions.assertEquals("png", ImageStorage.detectExtension(PNG));
		Assertions.assertEquals("jpg", ImageStorage.detectExtension(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0}));
		Assertions.assertEquals("webp", ImageStorage.detectExtension(
				new byte[] {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'}));
		Assertions.assertNull(ImageStorage.detectExtension(new byte[] {'G', 'I', 'F', '8', '9', 'a'}));
	}
}
