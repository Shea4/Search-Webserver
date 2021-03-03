package com.search;

import okhttp3.OkHttpClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;

@SpringBootApplication
public class WebServer {

	public static final String CONFIG = "src/main/resources/application.properties";

	private final static OkHttpClient CLIENT = new OkHttpClient();

	public static void main(String[] args) {
		ConfigurableApplicationContext context = SpringApplication.run(WebServer.class, "--spring.config.location=" + WebServer.CONFIG);
	}

	public static OkHttpClient getClient() {
		return WebServer.CLIENT;
	}

}
