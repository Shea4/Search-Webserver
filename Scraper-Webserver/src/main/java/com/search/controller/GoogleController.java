package com.search.controller;

import com.search.WebServer;
import com.search.config.Config;
import com.search.entities.HttpCallback;
import com.search.entities.JsonResponseEntity;
import com.search.entities.ResultType;
import okhttp3.Request;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@RestController
public class GoogleController {

	private final Config config = Config.get();

	private JSONObject getIcon(String url) {
		return new JSONObject().put("url", "https:" + url).put("file", url.substring(url.lastIndexOf('/') + 1));
	}

	@GetMapping(value="google", produces={"application/json"})
	public CompletableFuture<ResponseEntity<String>> google(@RequestParam(value="q") String query, @RequestParam(value="nsfw", required=false, defaultValue="false") boolean nsfw, @RequestParam(value="page", required=false, defaultValue="1") int page, @RequestParam(value="types", required=false) List<Integer> types) throws IOException {
		String url = "https://google.com/search?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8) + "&start=" + ((page - 1) * 10) + (nsfw ? "" : "&safe=active");

		Request request = new Request.Builder()
			.url(url)
			.addHeader("User-Agent", this.config.getProperty("google.user-agent"))
			.build();

		AtomicLong elapsed = new AtomicLong(System.currentTimeMillis());

		CompletableFuture<ResponseEntity<String>> future = new CompletableFuture<>();
		WebServer.getClient().newCall(request).enqueue((HttpCallback) response -> {
			Document document = Jsoup.parse(response.body().string());
			JSONArray results = new JSONArray();

			Element search = document.getElementById("rso");

			Elements divs = search == null ? new Elements() : search.getElementsByTag("div");
			for (Element div : divs) {
				if (div.className().equals("g") && (types == null || types.contains(ResultType.RESULT.getId()))) {
					Element titleElement = div.getElementsByTag("a").first();
					if (titleElement == null) {
						continue;
					}

					String titleUrl = titleElement.attr("href");
					Element title = titleElement.getElementsByTag("h3").first();
					if (title == null) {
						continue;
					}

					JSONObject data = new JSONObject().put("title", title.text())
						.put("url", titleUrl)
						.put("type", ResultType.RESULT.getId());

					Element previous = div.previousElementSibling();
					if (previous != null && previous.attr("data-md").equals("61")) {
						Element heading = previous.getElementsByAttributeValue("aria-level", "3").first();
						Element answer = previous.previousElementSibling();

						data.put("description", heading.getElementsByTag("span").first().text())
							.put("answer", answer == null ? null : answer.getElementsByAttributeValue("data-tts", "answers").first().text());
					} else {
						Element descriptionElement = div.getElementsByClass("IsZvec").first();
						Element span = descriptionElement.getElementsByClass("aCOpRe").first();

						data.put("description", span == null ? descriptionElement.text() : span.text());
					}

					results.put(data);
				} else if (div.attr("data-md").equals("2") && (types == null || types.contains(ResultType.WEATHER.getId()))) {
					JSONObject json = new JSONObject();
					json.put("type", ResultType.WEATHER.getId());

					Element weatherElement = div.getElementById("wob_wc");

					Element infoElement = weatherElement.getElementById("wob_d");
					Element stateElement = infoElement.getElementById("wob_tci");

					Element urlElement = div.getElementsByTag("td").first();
					if (urlElement != null) {
						json.put("url", urlElement.getElementsByTag("a").first().attr("href"));
					}

					json.put("location", weatherElement.getElementById("wob_loc").text());

					Element precipitationElement = infoElement.getElementById("wob_pp"), humidityElement = infoElement.getElementById("wob_hm"), windSpeedElement = infoElement.getElementById("wob_ws"), temperatureElement = infoElement.getElementById("wob_tm");
					String precipitation = precipitationElement == null ? null : precipitationElement.text(), humidity = humidityElement == null ? null : humidityElement.text(), windSpeed = windSpeedElement == null ? null : windSpeedElement.text();

					Element hourlyElement = weatherElement.getElementById("wob_gs");

					Element windElement = hourlyElement.getElementById("wob_wg");
					Element windDirection = windElement.getElementsByClass("wob_hw").first();
					Element image = windDirection.getElementsByTag("img").first();
					String windStyle = image.attr("style");

					int degreesIndex = windStyle.indexOf("transform:rotate(") + 17;
					int degrees = Integer.parseInt(windStyle.substring(degreesIndex, windStyle.indexOf("deg);", degreesIndex + 1))) % 360;

					JSONObject now = new JSONObject()
						.put("date_time", weatherElement.getElementById("wob_dts").text())
						.put("humidity", humidity == null ? null : Integer.parseInt(humidity.substring(0, humidity.length() - 1)))
						.put("precipitation", precipitation == null ? null : Integer.parseInt(precipitation.substring(0, precipitation.length() - 1)))
						.put("temperature", temperatureElement == null ? null : Integer.parseInt(temperatureElement.text()))
						.put("wind", new JSONObject().put("speed", windSpeed == null ? null : Integer.parseInt(windSpeed.substring(0, windSpeed.length() - 4))).put("direction", degrees % 360))
						.put("state", stateElement == null ? null : stateElement.attr("alt"))
						.put("icon", stateElement == null ? null : this.getIcon(stateElement.attr("src")));

					json.put("now", now);

					JSONArray daily = new JSONArray();
					Element dailyData = weatherElement.getElementById("wob_dp");

					Elements dailyElements = dailyData.getElementsByClass("wob_df");
					for (Element dailyElement : dailyElements) {
						Element state = dailyElement.getElementsByTag("img").first();
						Elements temperatures = dailyElement.getElementsByClass("wob_t");

						daily.put(
							new JSONObject()
								.put("day", dailyElement.getElementsByTag("div").get(1).attr("aria-label"))
								.put("icon", this.getIcon(state.attr("src")))
								.put("state", state.attr("alt"))
								.put("temperature", new JSONObject().put("high", Integer.parseInt(temperatures.get(0).text())).put("low", Integer.parseInt(temperatures.get(2).text())))
						);
					}

					json.put("daily", daily);

					results.put(json);
				} else if (div.attr("data-md").equals("14") && (types == null || types.contains(ResultType.DEFINITION.getId()))) {
					Element element = div.getElementsByAttribute("data-topic").first();

					Elements definitionElements = element.getElementsByClass("vmod").stream().filter(d -> d.hasAttr("data-topic")).collect(Collectors.toCollection(Elements::new));

					JSONArray definitions = new JSONArray();
					String lastType = null;
					for (Element definitionElement : definitionElements.subList(1, definitionElements.size())) {
						Element typeElement = definitionElement.getElementsByTag("li").first();

						String type = definitionElement.getElementsByTag("i").text();
						if (type.isEmpty()) {
							type = lastType;
						}

						lastType = type;

						Elements textElement = typeElement.getElementsByAttributeValue("data-dobid", "dfn");
						Elements exampleElements = textElement.next();

						Element similarElement = exampleElements.next().first();
						Element exampleElement = exampleElements.first();

						JSONObject definition = new JSONObject()
							.put("definition", textElement.first().text())
							.put("example", exampleElement.className().equals("vmod") ? exampleElement.text() : null)
							.put("type", type)
							.put("similar", similarElement != null && similarElement.hasAttr("jscontroller") ? similarElement.getElementsByAttributeValue("role", "listitem").eachText() : null);

						definitions.put(definition);
					}

					String word = element.getElementsByAttributeValue("data-dobid", "hdw").text();

					results.put(
						new JSONObject()
							.put("type", ResultType.DEFINITION.getId())
							.put("definitions", definitions)
							.put("word", word)
							.put("url", "https://www.lexico.com/definition/" + word)
							.put("pronunciation", "https:" + element.getElementsByTag("source").attr("src"))
					);
				} else if (div.attr("data-md").equals("126") && (types == null || types.contains(ResultType.ANSWER.getId()))) {
					Element descriptionElement = div.nextElementSibling();
					if (descriptionElement == null) {
						continue;
					}

					Element answerElement = descriptionElement.getElementsByAttributeValue("aria-level", "3").first();
					if (answerElement == null) {
						continue;
					}

					results.put(
						new JSONObject()
							.put("type", ResultType.ANSWER.getId())
							.put("title", div.getElementsByAttributeValue("aria-level", "2").first().text())
							.put("answer", answerElement.child(0).text())
					);
				} else if (div.className().equals("vk_c card obcontainer card-section") && (types == null || types.contains(ResultType.CONVERSION.getId()))) {
					String type = div.getElementsByTag("option").stream().filter(e -> e.attr("selected").equals("1")).map(Element::text).findFirst().orElse(null);

					Element inputElement = div.getElementById("HG5Seb");

					String inputUnit = inputElement.getElementsByTag("option").stream().filter(e -> e.attr("selected").equals("1")).map(Element::text).findFirst().orElse(null);
					double inputValue = Double.parseDouble(inputElement.getElementsByTag("input").first().attr("value"));

					JSONObject input = new JSONObject().put("value", inputValue).put("unit", inputUnit);

					Element outputElement = div.getElementById("NotFQb");

					String outputUnit = outputElement.getElementsByTag("option").stream().filter(e -> e.attr("selected").equals("1")).map(Element::text).findFirst().orElse(null);
					double outputValue = Double.parseDouble(outputElement.getElementsByTag("input").first().attr("value"));

					JSONObject output = new JSONObject().put("value", outputValue).put("unit", outputUnit);

					Element tableElement = div.getElementsByTag("table").first();
					Element formulaElement = tableElement.getElementsByTag("div").get(1);

					results.put(
						new JSONObject()
							.put("type", ResultType.CONVERSION.getId())
							.put("conversion_type", type)
							.put("input", input)
							.put("output", output)
							.put("formula", formulaElement.text())
					);
				} else if (div.id().equals("knowledge-currency__updatable-data-column") && (types == null || types.contains(ResultType.CONVERSION.getId()))) {
					Elements elements = div.getElementsByTag("tr").stream().filter(e -> e.className().isEmpty()).collect(Collectors.toCollection(Elements::new));

					Element inputElement = elements.first();

					String inputUnit = inputElement.getElementsByTag("option").stream().filter(e -> e.attr("selected").equals("1")).map(Element::text).findFirst().orElse(null);
					double inputValue = Double.parseDouble(inputElement.getElementsByTag("input").first().attr("value"));

					JSONObject input = new JSONObject().put("value", inputValue).put("unit", inputUnit);

					Element outputElement = elements.get(1);

					String outputUnit = outputElement.getElementsByTag("option").stream().filter(e -> e.attr("selected").equals("1")).map(Element::text).findFirst().orElse(null);
					double outputValue = Double.parseDouble(outputElement.getElementsByTag("input").first().attr("value"));

					JSONObject output = new JSONObject().put("value", outputValue).put("unit", outputUnit);

					results.put(
						new JSONObject()
							.put("type", ResultType.CONVERSION.getId())
							.put("conversion_type", "Currency")
							.put("input", input)
							.put("output", output)
					);
				} else if (div.attr("data-md").equals("74") && (types == null || types.contains(ResultType.DATE_TIME.getId()))) {
					Element dateTimeElement = div.getElementsByAttributeValueContaining("class", "vk_bk").first();
					if (dateTimeElement == null) {
						continue;
					}

					Element span = dateTimeElement.getElementsByTag("span").first();
					Element dateElement = div.getElementsByClass("vk_gy vk_sh").first();

					if (dateTimeElement.attr("aria-level").equals("3")) {
						results.put(
							new JSONObject()
								.put("type", ResultType.DATE_TIME.getId())
								.put("time", dateTimeElement.text())
								.put("date", dateElement.text())
						);
					} else {
						results.put(
							new JSONObject()
								.put("type", ResultType.DATE_TIME.getId())
								.put("date", span == null ? dateTimeElement.text() + ", " + dateElement.text() : dateTimeElement.text())
						);
					}
				} else if (div.attr("data-md").equals("279") && (types == null || types.contains(ResultType.CALCULATOR.getId()))) {
					Elements calcElement = div.getElementsByAttributeValue("aria-label", "calculations history");
					if (calcElement == null) {
						continue;
					}

					Element equationElement = calcElement.next().first();
					if (equationElement == null) {
						continue;
					}

					String equation = equationElement.text();

					results.put(
						new JSONObject()
							.put("type", ResultType.CALCULATOR.getId())
							.put("answer", Double.parseDouble(div.getElementById("cwos").text()))
							.put("equation", equation.substring(0, equation.length() - 2))
					);
				} else if (div.attr("data-md").equals("77") && (types == null || types.contains(ResultType.TRANSLATE.getId()))) {
					Element languageElement = div.getElementById("tw-plp");
					Element textElement = div.getElementById("tw-ob");

					Element inputTextElement = textElement.getElementById("tw-source-text-ta");
					Element inputLanguageElement = languageElement.getElementById("tw-sl");

					JSONObject input = new JSONObject()
						.put("language", new JSONObject().put("name", inputLanguageElement.attr("data-dsln")).put("iso_code", inputLanguageElement.attr("data-dslc")))
						.put("detected", inputLanguageElement.attr("data-lang").equals("auto"))
						.put("text", inputTextElement.text());

					Element outputTextElement = textElement.getElementById("tw-target-text-container");

					Element outputLanguageElement = languageElement.getElementById("tw-tl");
					Element languageNameElement = outputLanguageElement.getElementsByClass("target-language").first();

					JSONObject output = new JSONObject()
						.put("language", new JSONObject().put("name", languageNameElement.text()).put("iso_code", outputLanguageElement.attr("data-lang")))
						.put("text", outputTextElement.text());

					results.put(
						new JSONObject()
							.put("type", ResultType.TRANSLATE.getId())
							.put("input", input)
							.put("output", output)
					);
				} else if (div.attr("data-md").equals("137") && (types == null || types.contains(ResultType.RANDOM_NUMBER.getId()))) {
					Element randomElement = div.getElementById("Zv1Nfb");

					results.put(
						new JSONObject()
							.put("type", ResultType.RANDOM_NUMBER.getId())
							.put("value", Integer.parseInt(randomElement.getElementsByClass("gws-csf-randomnumber__result").first().text()))
							.put("min", Integer.parseInt(randomElement.getElementById("UMy8j").attr("value")))
							.put("max", Integer.parseInt(randomElement.getElementById("nU5Yvb").attr("value")))
					);
				} else if (div.id().equals("lud-dsu") && (types == null || types.contains(ResultType.DIRECTIONS.getId()))) {
					Element selector = div.getElementsByClass("gws-plugins-local-jslayout-mode_selector__selector").first();
					Element selected = selector.getElementsByAttributeValue("aria-selected", "true").first();
					Elements inputs = div.getElementsByTag("input");
					Element map = div.getElementsByClass("rrm").first();

					String method = selected.getElementsByTag("img").first().attr("title");

					Element routesElement = div.getElementsByAttributeValue("data-async-type", "routeSearch").first();

					JSONArray routes = new JSONArray();
					for (Element route : routesElement.getElementsByAttributeValueStarting("data-rre-id", "exp")) {
						if (!route.tagName().equals("div")) {
							continue;
						}

						if (method.equals("Public transport")) {
							Elements divElements = route.getElementsByTag("div");

							String[] durationSplit = divElements.get(2).text().split(" ");

							long duration = 0;
							for (int i = 0; i < durationSplit.length / 2; i++) {
								duration += Integer.parseInt(durationSplit[i * 2]) * (durationSplit[i * 2 + 1].equals("min") ? 60 : 3600);
							}

							String[] timeSplit = divElements.get(3).text().split(" - ");
							String[] stationSplit = divElements.get(5).text().split(" from ");

							routes.put(
								new JSONObject()
									.put("duration", duration)
									.put("depart", new JSONObject().put("location", timeSplit[0]).put("station", stationSplit[0]))
									.put("station", stationSplit[1])
									.put("arrive", timeSplit[1])
							);
						} else {
							Elements spanElements = route.getElementsByTag("span");

							String[] durationSplit = spanElements.get(1).text().split(" ");

							long duration = 0;
							for (int i = 0; i < durationSplit.length / 2; i++) {
								duration += Integer.parseInt(durationSplit[i * 2]) * (durationSplit[i * 2 + 1].equals("min") ? 60 : 3600);
							}

							JSONArray steps = new JSONArray();
							for (Element step : div.getElementsByClass("numbered-step-start").first().children()) {
								String[] distanceSplit = step.getElementsByClass("uW1II").first().text().split(" ");

								steps.put(
									new JSONObject()
										.put("instruction", step.getElementsByClass("numbered-step").first().textNodes().get(0).text())
										.put("in", new JSONObject().put("value", Double.parseDouble(distanceSplit[0])).put("unit", distanceSplit[1]))
								);
							}

							String[] distanceSplit = spanElements.get(2).text().split(" ");

							routes.put(
								new JSONObject()
									.put("duration", duration)
									.put("distance", new JSONObject().put("value", Double.parseDouble(distanceSplit[0].replace(",", ""))).put("unit", distanceSplit[1]))
									.put("via", spanElements.get(3).text().substring("Via ".length()))
									.put("steps", steps)
							);
						}
					}

					results.put(
						new JSONObject()
							.put("to", inputs.get(1).attr("placeholder"))
							.put("from", inputs.get(0).attr("placeholder"))
							.put("method", method)
							.put("url", "https://google.com" + map.getElementsByTag("a").first().attr("data-url"))
							.put("routes", routes)
							.put("type", ResultType.DIRECTIONS.getId())
					);
				} else if (div.attr("jsname").equals("MxBDOc") && (types == null || types.contains(ResultType.FLIGHTS.getId()))) {
					Elements boxes = div.getElementsByAttribute("data-is-mobile");
					if (boxes.isEmpty()) {
						continue;
					}

					Elements locations = boxes.get(0).getElementsByTag("input"), dates = boxes.get(1).getElementsByTag("input");

					String returnDate = dates.get(1).attr("value");

					JSONArray flights = new JSONArray();
					for (Element flight : div.getElementsByTag("a")) {
						Elements divElements = flight.getElementsByTag("div");

						String[] durationSplit = divElements.get(1).text().split(" ");

						long duration = 0;
						for (String part : durationSplit) {
							part = part.contains("+") ? part.substring(0, part.length() - 1) : part;

							char unit = part.charAt(part.length() - 1);
							duration += Integer.parseInt(part.substring(0, part.length() - 1)) * (unit == 'h' ? 3600 : 60);
						}

						String price = divElements.get(3).getElementsByTag("span").first().text();

						int currencyLength = 0;
						for (char character : price.toCharArray()) {
							if (!Character.isDigit(character)) {
								currencyLength++;
							} else {
								break;
							}
						}

						flights.put(
							new JSONObject()
								.put("url", flight.attr("href"))
								.put("airline", divElements.get(0).text())
								.put("price", new JSONObject().put("value", Integer.parseInt(price.substring(currencyLength))).put("formatted", price))
								.put("type", divElements.get(2).text())
								.put("duration", duration)
						);
					}

					results.put(
						new JSONObject()
							.put("departing", locations.get(0).attr("value"))
							.put("destination", locations.get(1).attr("value"))
							.put("return", returnDate.isEmpty() ? JSONObject.NULL : returnDate)
							.put("departure", dates.get(0).attr("value"))
							.put("flights", flights)
							.put("type", ResultType.FLIGHTS.getId())
					);
				}
			}

			future.complete(JsonResponseEntity.ok(new JSONObject().put("results", results).put("url", url).put("elapsed", System.currentTimeMillis() - elapsed.get())));
		});

		return future;
	}

}
