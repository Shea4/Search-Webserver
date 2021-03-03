package com.search.config;

import com.search.WebServer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;

public class Config {

	private static final Config INSTANCE = new Config();

	public static Config get() {
		return Config.INSTANCE;
	}

	private final Properties properties;

	private Config() {
		this.properties = new Properties();
		this.load(WebServer.CONFIG);
	}

	public void load(String path) {
		try (FileInputStream stream = new FileInputStream(path)) {
			this.properties.load(stream);
		} catch (IOException e) {
			System.err.println("Failed to load properties file");
		}
	}

	public <Type> Type getProperty(String key) {
		return this.getProperty(key, null);
	}

	@SuppressWarnings("unchecked")
	public <Type> Type getProperty(String key, Type defaultValue) {
		Type property = (Type) this.properties.getProperty(key);
		return property == null ? defaultValue : property;
	}

}
