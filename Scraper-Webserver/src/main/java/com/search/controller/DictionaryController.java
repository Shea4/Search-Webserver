package com.search.controller;

import com.search.WebServer;
import com.search.entities.HttpCallback;
import com.search.entities.JsonResponseEntity;
import okhttp3.Request;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@RestController
public class DictionaryController {

	@GetMapping(value="dictionary", produces={"application/json"})
	public CompletableFuture<ResponseEntity<String>> dictionary(@RequestParam(value="q") String query) {
		String url = "https://www.oxfordlearnersdictionaries.com/definition/english/" + URLEncoder.encode(query, StandardCharsets.UTF_8);

		Request request = new Request.Builder()
			.url(url)
			.build();

		CompletableFuture<ResponseEntity<String>> future = new CompletableFuture<>();
		WebServer.getClient().newCall(request).enqueue((HttpCallback) response -> {
			if (!response.isSuccessful() && response.code() != 404) {
				future.complete(new JsonResponseEntity(response.body().string(), 500));
				return;
			}

			Document document = Jsoup.parse(response.body().string());

			Element sensesMultiple = document.getElementsByClass("senses_multiple").first();
			if (sensesMultiple == null) {
				future.complete(JsonResponseEntity.ok(new JSONObject().put("definitions", new JSONArray()).put("status", 200)));
				return;
			}

			JSONObject json = new JSONObject();
			json.put("word", query);
			json.put("url", url);
			json.put("type", document.getElementsByClass("pos").first().text());

			Element pronunciationElement = document.getElementsByClass("sound audio_play_button pron-uk icon-audio").first();
			json.put("pronunciation", pronunciationElement == null ? null : pronunciationElement.attr("data-src-mp3"));

			JSONArray definitions = new JSONArray();

			Elements senses = sensesMultiple.getElementsByClass("sense");
			for (Element sense : senses) {
				Element definitionElement = sense.getElementsByClass("def").first();
				if (definitionElement == null) {
					continue;
				}

				Elements referenceElements = definitionElement.getElementsByClass("Ref");
				List<TextNode> nodes = definitionElement.textNodes();

				JSONArray textNodes = new JSONArray();
				for (int i = 0; i < Math.max(1, nodes.size()); i++) {
					if (i < nodes.size()) {
						textNodes.put(new JSONObject().put("text", nodes.get(i).text()));
					}

					if (i < referenceElements.size()) {
						Element reference = referenceElements.get(i);

						textNodes.put(new JSONObject().put("text", reference.getElementsByClass("ndv").first().text()).put("url",  reference.attr("href")));
					}
				}

				JSONArray examples = new JSONArray();

				Element exampleElement = sense.getElementsByClass("examples").first();
				if (exampleElement != null) {
					Elements exampleElements = exampleElement.getElementsByTag("li");
					for (Element example : exampleElements) {
						examples.put(example.text());
					}
				}

				definitions.put(new JSONObject().put("nodes", textNodes).put("examples", examples));
			}

			future.complete(JsonResponseEntity.ok(json.put("definitions", definitions).put("status", 200)));
		});

		return future;
	}

}
