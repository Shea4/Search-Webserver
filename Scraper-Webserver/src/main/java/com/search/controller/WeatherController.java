package com.search.controller;

import com.search.WebServer;
import com.search.entities.HttpCallback;
import com.search.entities.JsonResponseEntity;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Iterator;
import java.util.concurrent.CompletableFuture;

@RestController
public class WeatherController {

	@GetMapping(value="weather", produces={"application/json"})
	public CompletableFuture<ResponseEntity<String>> weather(@RequestParam(value="q") String query) {
		JSONObject locationBody = new JSONObject()
			.put("name", "getSunV3LocationSearchUrlConfig")
			.put("params", new JSONObject().put("locationType", "locale").put("query", query));

		Request locationRequest = new Request.Builder()
			.url("https://weather.com/api/v1/p/redux-dal")
			.post(RequestBody.create(new JSONArray().put(locationBody).toString(), MediaType.parse("application/json")))
			.build();

		CompletableFuture<ResponseEntity<String>> future = new CompletableFuture<>();
		WebServer.getClient().newCall(locationRequest).enqueue((HttpCallback) locationResponse -> {
			JSONObject json = new JSONObject(locationResponse.body().string())
				.getJSONObject("dal")
				.getJSONObject("getSunV3LocationSearchUrlConfig")
				.getJSONObject("locationType:locale;query:" + query);

			if (json.getInt("status") == 404) {
				future.complete(new JsonResponseEntity("Location not found", 404));
				return;
			}

			JSONObject locationData = json.getJSONObject("data").getJSONObject("location");

			JSONObject data = new JSONObject()
				.put("url", "https://weather.com/en-GB/weather/today/l/" + locationData.getJSONArray("placeId").getString(0))
				.put("location", locationData.getJSONArray("address").getString(0));

			double latitude = locationData.getJSONArray("latitude").getDouble(0), longitude = locationData.getJSONArray("longitude").getDouble(0);

			JSONObject fifteenMinBody = new JSONObject()
				.put("name", "getSun15MinForecastByGeocodeUrlConfig")
				.put("params", new JSONObject().put("longitude", longitude).put("latitude", latitude).put("language", "en-GB").put("units", "h"));

			Request fifteenMinRequest = new Request.Builder()
				.url("https://weather.com/api/v1/p/redux-dal")
				.post(RequestBody.create(new JSONArray().put(fifteenMinBody).toString(), MediaType.parse("application/json")))
				.build();

			WebServer.getClient().newCall(fifteenMinRequest).enqueue((HttpCallback) fifteenMinResponse -> {
				JSONObject fifteenMinData = new JSONObject(fifteenMinResponse.body().string())
					.getJSONObject("dal")
					.getJSONObject("getSun15MinForecastByGeocodeUrlConfig")
					.getJSONObject("language:en-GB;latitude:" + latitude + ";longitude:" + longitude + ";units:h")
					.getJSONObject("data")
					.getJSONArray("forecasts")
					.getJSONObject(0);

				JSONObject now = new JSONObject();

				Iterator<String> fifteenMinKeys = fifteenMinData.keys();
				while (fifteenMinKeys.hasNext()) {
					String key = fifteenMinKeys.next();
					Object value = fifteenMinData.get(key);

					if (key.equals("icon_code")) {
						now.put("icon", "https://smartthings-twc-icons.s3.amazonaws.com/" + value + ".png");
					}

					now.put(key, value);
				}

				data.put("now", now);

				JSONObject dailyBody = new JSONObject()
					.put("name", "getSunDailyForecastByGeocodeUrlConfig")
					.put("params", new JSONObject().put("longitude", longitude).put("latitude", latitude).put("language", "en-GB").put("units", "h").put("days", 3));

				Request dailyRequest = new Request.Builder()
					.url("https://weather.com/api/v1/p/redux-dal")
					.post(RequestBody.create(new JSONArray().put(dailyBody).toString(), MediaType.parse("application/json")))
					.build();

				WebServer.getClient().newCall(dailyRequest).enqueue((HttpCallback) dailyResponse -> {
					JSONObject todayData = new JSONObject(dailyResponse.body().string())
						.getJSONObject("dal")
						.getJSONObject("getSunDailyForecastByGeocodeUrlConfig")
						.getJSONObject("days:3;language:en-GB;latitude:" + latitude + ";longitude:" + longitude + ";units:h")
						.getJSONObject("data")
						.getJSONArray("forecasts")
						.getJSONObject(0);

					JSONObject today = new JSONObject();

					Iterator<String> todayKeys = todayData.keys();
					while (todayKeys.hasNext()) {
						String key = todayKeys.next();
						today.put(key, todayData.get(key));
					}

					data.put("today", today);

					future.complete(JsonResponseEntity.ok(data));
				});
			});
		});

		return future;
	}

}
