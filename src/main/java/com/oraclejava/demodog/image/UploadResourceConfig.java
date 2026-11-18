package com.oraclejava.demodog.image;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 업로드 폴더를 /uploads/** 로 서빙한다. 재시작 없이 새 파일이 보인다. */
@Configuration
public class UploadResourceConfig implements WebMvcConfigurer {

	private final ImageStorage storage;

	public UploadResourceConfig(ImageStorage storage) {
		this.storage = storage;
	}

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		String location = storage.root().toUri().toString();
		if (!location.endsWith("/")) {
			location += "/";
		}
		registry.addResourceHandler("/uploads/**").addResourceLocations(location);
	}
}
