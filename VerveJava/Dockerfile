# Use OpenJDK with Alpine as base image
FROM openjdk:17-jdk-alpine

# Install necessary tools
RUN apk add --no-cache bash wget tar procps

# Install and setup Redis
RUN wget https://download.redis.io/releases/redis-6.2.6.tar.gz && \
    tar xzf redis-6.2.6.tar.gz && \
    cd redis-6.2.6 && \
    apk add --no-cache make gcc musl-dev && \
    make && \
    cp src/redis-server /usr/local/bin/ && \
    cd .. && \
    rm -rf redis-6.2.6 redis-6.2.6.tar.gz

# Install and setup Kafka
RUN wget https://archive.apache.org/dist/kafka/3.3.1/kafka_2.13-3.3.1.tgz && \
    tar xzf kafka_2.13-3.3.1.tgz && \
    mv kafka_2.13-3.3.1 /opt/kafka && \
    rm kafka_2.13-3.3.1.tgz

# Create a custom server.properties to use port 9090
RUN echo "broker.id=1" > /opt/kafka/config/server.properties && \
    echo "listeners=PLAINTEXT://0.0.0.0:9090" >> /opt/kafka/config/server.properties && \
    echo "advertised.listeners=PLAINTEXT://localhost:9090" >> /opt/kafka/config/server.properties && \
    echo "log.dirs=/tmp/kafka-logs" >> /opt/kafka/config/server.properties && \
    echo "zookeeper.connect=localhost:2181" >> /opt/kafka/config/server.properties && \
    echo "offsets.topic.replication.factor=1" >> /opt/kafka/config/server.properties && \
    echo "default.replication.factor=1" >> /opt/kafka/config/server.properties

# Set working directory
WORKDIR /app

# Set environment variables
ENV JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF8"
ENV SERVER_PORT=8080
ENV REDIS_HOST=localhost
ENV REDIS_PORT=6379
ENV KAFKA_BOOTSTRAP_SERVERS=localhost:9090

# Copy the pre-built JAR
COPY target/VerveJava-1.0-SNAPSHOT-jar-with-dependencies.jar VerveJava-1.0-SNAPSHOT-jar-with-dependencies.jar

# Copy application.properties
COPY src/main/resources/application.properties application.properties

# Create and copy start script
RUN echo '#!/bin/bash' > start.sh && \
    echo '# Start Redis in the background' >> start.sh && \
    echo 'redis-server &' >> start.sh && \
    echo 'echo "Redis server started"' >> start.sh && \
    echo '' >> start.sh && \
    echo '# Start Zookeeper (required for Kafka) in the background' >> start.sh && \
    echo '/opt/kafka/bin/zookeeper-server-start.sh /opt/kafka/config/zookeeper.properties &' >> start.sh && \
    echo 'echo "Zookeeper started"' >> start.sh && \
    echo '' >> start.sh && \
    echo '# Wait a moment to ensure Zookeeper is up' >> start.sh && \
    echo 'sleep 5' >> start.sh && \
    echo '' >> start.sh && \
    echo '# Start Kafka server in the background' >> start.sh && \
    echo '/opt/kafka/bin/kafka-server-start.sh /opt/kafka/config/server.properties &' >> start.sh && \
    echo 'echo "Kafka server started"' >> start.sh && \
    echo '' >> start.sh && \
    echo '# Wait a moment to ensure Kafka is up' >> start.sh && \
    echo 'sleep 5' >> start.sh && \
    echo '' >> start.sh && \
    echo '# Start the Java application' >> start.sh && \
    echo 'java -jar VerveJava-1.0-SNAPSHOT-jar-with-dependencies.jar' >> start.sh && \
    chmod +x start.sh

# Expose ports
EXPOSE 8080 6379 9090

# Use the startup script as the entrypoint
ENTRYPOINT ["./start.sh"]