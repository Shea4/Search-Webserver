package com.search;

import okhttp3.OkHttpClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;

@SpringBootApplication
public class WebServer {

	private final static OkHttpClient CLIENT = new OkHttpClient();

	public static void main(String[] args) {
		SpringApplication.run(WebServer.class, args);
	}

	public static OkHttpClient getClient() {
		return WebServer.CLIENT;
	}

}
