package com.oraclejava.demodog.image;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 품종 이름으로 나무위키 문서에서 이미지 후보를 찾고, 로컬 Ollama 모델에게 1장을 고르게 한 뒤 저장한다.
 * 모델이 텍스트 전용이라 사진 픽셀은 보지 못하고 alt와 순서만으로 고른다. 모델이 실패하면 첫 후보를 쓴다.
 * 호출 1회당 나무위키 요청은 문서 1건 + 이미지 1건뿐이다(대량 수집 아님).
 */
@Service
public class NamuImagePipeline {

	/** 파이프라인 결과. source는 이미지를 찾은 문서 URL이다. */
	public record Result(String image, String source) {
	}

	/** 문서에서 찾은 이미지 후보. */
	record Candidate(String url, String alt) {
	}

	private static final Logger log = LoggerFactory.getLogger(NamuImagePipeline.class);

	static final String WIKI_BASE = "https://namu.wiki/w/";
	static final String IMAGE_HOST = "i.namu.wiki";
	private static final int MAX_CANDIDATES = 10;
	private static final int MAX_PAGE_BYTES = 5 * 1024 * 1024;
	private static final String USER_AGENT = "Mozilla/5.0 (compatible; demo-dog-image-fetcher)";
	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	private static final Pattern IMG_TAG = Pattern.compile("<img\\b[^>]*>", Pattern.CASE_INSENSITIVE);
	private static final String URL_BODY = "(?:https:)?//i\\.namu\\.wiki/i/[A-Za-z0-9_\\-]+\\.(?:webp|jpe?g|png)";
	private static final Pattern URL_ATTR = Pattern.compile(
			"(?:data-src|src)\\s*=\\s*['\"](" + URL_BODY + ")['\"]", Pattern.CASE_INSENSITIVE);
	private static final Pattern ALT_ATTR = Pattern.compile("\\balt\\s*=\\s*['\"]([^'\"]*)['\"]",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern RAW_URL = Pattern.compile(URL_BODY, Pattern.CASE_INSENSITIVE);

	private final ImageStorage storage;
	private final String ollamaUrl;
	private final String model;
	/** 문서와 Ollama 호출용. */
	private final HttpClient client = HttpClient.newBuilder()
			.followRedirects(HttpClient.Redirect.NORMAL)
			.connectTimeout(Duration.ofSeconds(10))
			.build();
	/** 이미지 다운로드용. 허용 호스트 검사를 우회하지 못하도록 리다이렉트를 따라가지 않는다. */
	private final HttpClient imageClient = HttpClient.newBuilder()
			.followRedirects(HttpClient.Redirect.NEVER)
			.connectTimeout(Duration.ofSeconds(10))
			.build();

	public NamuImagePipeline(ImageStorage storage,
			@Value("${app.ollama.url:http://localhost:11434}") String ollamaUrl,
			@Value("${app.ollama.model:qwen2.5-coder:7b}") String model) {
		this.storage = storage;
		this.ollamaUrl = ollamaUrl;
		this.model = model;
	}

	public Result fetch(String kind) {
		if (kind == null || kind.isBlank() || kind.length() > 100) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "kind is required (max 100 chars)");
		}
		String pageUrl = WIKI_BASE + URLEncoder.encode(kind.trim(), StandardCharsets.UTF_8).replace("+", "%20");
		String html = new String(download(client, pageUrl, MAX_PAGE_BYTES), StandardCharsets.UTF_8);

		List<Candidate> candidates = extractCandidates(html);
		if (candidates.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no image candidates found in the document");
		}

		int index = choose(kind.trim(), candidates);
		String imageUrl = candidates.get(index).url();
		if (!isAllowedImageUrl(imageUrl)) {
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "image host is not allowed");
		}
		byte[] data = download(imageClient, imageUrl, ImageStorage.MAX_BYTES);
		return new Result(storage.save(data), pageUrl);
	}

	/** 본문 img 태그에서 후보를 모은다. 태그에서 못 찾으면 원문 전체에서 URL만 찾는다. svg와 중복은 제외한다. */
	static List<Candidate> extractCandidates(String html) {
		Map<String, String> found = new LinkedHashMap<>();
		Matcher tags = IMG_TAG.matcher(html);
		while (tags.find() && found.size() < MAX_CANDIDATES) {
			String tag = tags.group();
			Matcher url = URL_ATTR.matcher(tag);
			if (!url.find()) {
				continue;
			}
			Matcher alt = ALT_ATTR.matcher(tag);
			found.putIfAbsent(normalize(url.group(1)), alt.find() ? alt.group(1) : "");
		}
		if (found.isEmpty()) {
			Matcher raw = RAW_URL.matcher(html);
			while (raw.find() && found.size() < MAX_CANDIDATES) {
				found.putIfAbsent(normalize(raw.group()), "");
			}
		}
		List<Candidate> result = new ArrayList<>();
		found.forEach((url, alt) -> result.add(new Candidate(url, alt)));
		return result;
	}

	private static String normalize(String url) {
		return url.startsWith("//") ? "https:" + url : url;
	}

	/** https이고 호스트가 정확히 i.namu.wiki일 때만 허용한다(SSRF 방지). */
	static boolean isAllowedImageUrl(String url) {
		try {
			URI uri = URI.create(url);
			return "https".equals(uri.getScheme()) && IMAGE_HOST.equals(uri.getHost());
		} catch (IllegalArgumentException e) {
			return false;
		}
	}

	/** Ollama가 고른 후보 번호. 실패하거나 응답이 이상하면 첫 후보(0)를 쓴다. */
	private int choose(String kind, List<Candidate> candidates) {
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(ollamaUrl + "/api/chat"))
					.timeout(Duration.ofSeconds(60))
					.header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(buildChatBody(model, kind, candidates),
							StandardCharsets.UTF_8))
					.build();
			HttpResponse<String> response = client.send(request,
					HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (response.statusCode() == 200) {
				OptionalInt picked = parseChoice(response.body(), candidates.size());
				if (picked.isPresent()) {
					return picked.getAsInt();
				}
			}
			log.warn("Ollama gave no usable choice (HTTP {}), using the first candidate", response.statusCode());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (IOException | RuntimeException e) {
			log.warn("Ollama call failed, using the first candidate: {}", e.toString());
		}
		return 0;
	}

	static String buildChatBody(String model, String kind, List<Candidate> candidates) {
		StringBuilder prompt = new StringBuilder();
		prompt.append("Dog breed: ").append(oneLine(kind)).append("\n")
				.append("Image candidates from its wiki page:\n");
		for (int i = 0; i < candidates.size(); i++) {
			prompt.append(i).append(": alt=\"").append(oneLine(candidates.get(i).alt())).append("\"\n");
		}
		prompt.append("Pick the single candidate most likely to be a photo of this dog breed ")
				.append("(not an icon, logo, map, chart or illustration). ")
				.append("If unsure, pick 0. Reply only with JSON like {\"index\": 0}.");
		return MAPPER.writeValueAsString(Map.of(
				"model", model,
				"stream", false,
				"format", "json",
				"options", Map.of("temperature", 0),
				"messages", List.of(Map.of("role", "user", "content", prompt.toString()))));
	}

	/** Ollama /api/chat 응답에서 후보 번호를 꺼낸다. 형식이 맞지 않거나 범위를 벗어나면 비어 있다. */
	static OptionalInt parseChoice(String ollamaBody, int size) {
		try {
			String content = MAPPER.readTree(ollamaBody).path("message").path("content").asText("");
			JsonNode index = MAPPER.readTree(content).path("index");
			if (!index.isIntegralNumber()) {
				return OptionalInt.empty();
			}
			int i = index.asInt();
			return i >= 0 && i < size ? OptionalInt.of(i) : OptionalInt.empty();
		} catch (RuntimeException e) {
			return OptionalInt.empty();
		}
	}

	/** 페이지의 alt 텍스트는 외부 데이터이므로 한 줄·짧게만 프롬프트에 넣는다. 응답은 번호만 해석한다. */
	private static String oneLine(String text) {
		String s = text == null ? "" : text.replaceAll("[\\r\\n\\p{Cntrl}\"]+", " ").trim();
		return s.length() > 80 ? s.substring(0, 80) : s;
	}

	private static byte[] download(HttpClient httpClient, String url, int maxBytes) {
		HttpRequest request = HttpRequest.newBuilder(URI.create(url))
				.timeout(Duration.ofSeconds(15))
				.header("User-Agent", USER_AGENT)
				.GET()
				.build();
		try {
			HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
			try (InputStream in = response.body()) {
				int status = response.statusCode();
				if (status == 404) {
					throw new ResponseStatusException(HttpStatus.NOT_FOUND, "document or image not found");
				}
				if (status != 200) {
					throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "upstream returned HTTP " + status);
				}
				byte[] data = in.readNBytes(maxBytes + 1);
				if (data.length > maxBytes) {
					throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "upstream response is too large");
				}
				return data;
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "interrupted");
		} catch (IOException e) {
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "upstream request failed");
		}
	}
}
