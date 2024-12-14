package com.example.config;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import java.util.Properties;
import java.io.IOException;
import java.io.FileInputStream;
import java.util.logging.Logger;

public class KafkaConfig {

    private String kafkaBootstrapServers;
    private KafkaProducer<String, String> kafkaProducer;

    public KafkaConfig() throws IOException {
        loadConfig();
        setupKafkaProducer();
    }

    private void loadConfig() throws IOException {
        kafkaBootstrapServers = System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "localhost:9090");

        if (kafkaBootstrapServers == null) {
            Properties properties = new Properties();
            try (FileInputStream input = new FileInputStream("application.properties")) {
                properties.load(input);
                kafkaBootstrapServers = properties.getProperty("kafka.bootstrap.servers", "localhost:9090");
            }
        }
    }

    private void setupKafkaProducer() {
        Properties kafkaProperties = new Properties();
        kafkaProperties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers);
        kafkaProperties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        kafkaProperties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        kafkaProducer = new KafkaProducer<>(kafkaProperties);
    }

    public void sendMessage(String topic, String message) {
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, message);
        kafkaProducer.send(record, (metadata, exception) -> {
            if (exception != null) System.out.println("Failed to send message to Kafka: " + exception.getMessage());
            else System.out.println("Successfully sent message to Kafka: " + metadata.topic());
        });
    }
}
