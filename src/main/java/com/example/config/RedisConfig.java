package com.example.config;

import redis.clients.jedis.Jedis;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class RedisConfig {

    private String redisHost;
    private int redisPort;
    private Jedis jedis;

    public RedisConfig() throws IOException {
        loadConfig();
        this.jedis = new Jedis(redisHost, redisPort);
    }

    private void loadConfig() throws IOException {
        redisHost = System.getenv().getOrDefault("REDIS_HOST", "localhost");
        redisPort = Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6379"));

        if (redisHost == null) {
            Properties properties = new Properties();
            try (FileInputStream input = new FileInputStream("application.properties")) {
                properties.load(input);
                redisHost = properties.getProperty("redis.host", "localhost");
                redisPort = Integer.parseInt(properties.getProperty("redis.port", "6379"));
            }
        }
    }

    public Jedis getJedis() {
        return jedis;
    }
}

