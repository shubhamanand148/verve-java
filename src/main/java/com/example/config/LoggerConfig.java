package com.example.config;

import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class LoggerConfig {

    private Logger logger;

    public LoggerConfig() throws IOException {
        setupLogging();
    }

    private void setupLogging() throws IOException {
        logger = Logger.getLogger(LoggerConfig.class.getName());
        FileHandler fileHandler = new FileHandler("request_count_log.log", true);
        fileHandler.setFormatter(new SimpleFormatter());
        logger.addHandler(fileHandler);
        logger.setLevel(java.util.logging.Level.INFO);
    }

    public Logger getLogger() {
        return logger;
    }
}
