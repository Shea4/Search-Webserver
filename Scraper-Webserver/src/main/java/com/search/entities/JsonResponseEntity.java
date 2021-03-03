package com.search.entities;

import org.json.JSONObject;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;

public class JsonResponseEntity extends ResponseEntity<String> {

	public static final MultiValueMap<String, String> DEFAULT_HEADERS = new HttpHeaders();
	static {
		JsonResponseEntity.DEFAULT_HEADERS.add("Content-Type", "application/json");
	}

	public JsonResponseEntity(JSONObject json, MultiValueMap<String, String> headers, int status) {
		super(json.put("status", status).toString(), headers, HttpStatus.valueOf(status));
	}

	public JsonResponseEntity(String message, MultiValueMap<String, String> headers, int status) {
		this(new JSONObject().put("status", status).put("message", message), headers, status);
	}

	public JsonResponseEntity(String message, int status) {
		this(new JSONObject().put("status", status).put("message", message), JsonResponseEntity.DEFAULT_HEADERS, status);
	}

	public JsonResponseEntity(JSONObject json, int status) {
		this(json, JsonResponseEntity.DEFAULT_HEADERS, status);
	}

	public static JsonResponseEntity ok(JSONObject json) {
		return new JsonResponseEntity(json,200);
	}

}
