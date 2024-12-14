package com.example;

import com.example.config.KafkaConfig;
import com.example.config.LoggerConfig;
import com.example.config.RedisConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.CharsetUtil;
import redis.clients.jedis.Jedis;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.time.Duration;
import java.net.http.HttpClient;

public class Controller {

    private final RedisConfig redisConfig;
    private final KafkaConfig kafkaConfig;
    private final LoggerConfig loggingConfig;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final Jedis jedis;
    private final Semaphore semaphore = new Semaphore(1);
    private final Logger logger;
    private int port;

    public Controller() throws IOException {
        // Initialize configurations
        redisConfig = new RedisConfig();
        kafkaConfig = new KafkaConfig();
        loggingConfig = new LoggerConfig();

        jedis = redisConfig.getJedis();
        logger = loggingConfig.getLogger();

        loadConfig();
        startLoggingTask();
    }

    private void loadConfig() {
        port = Integer.parseInt(System.getenv().getOrDefault("SERVER_PORT", "8080"));
    }

    private void startLoggingTask() {
        scheduler.scheduleAtFixedRate(this::sendUniqueRequestCountToKafka, 1, 1, TimeUnit.MINUTES);
    }

    private void sendUniqueRequestCountToKafka() {
        String countStr = jedis.get("unique_id_count");
        int uniqueRequestIds = (countStr != null) ? Integer.parseInt(countStr) : 0;

        String message = "Unique request count in the last minute: " + uniqueRequestIds;
        kafkaConfig.sendMessage("unique-request-count-topic", message);

        jedis.del("unique_ids");
        jedis.del("unique_id_count");
    }

    public static void main(String[] args) throws Exception {
        new Controller().start();
    }

    public void start() throws Exception {
        EventLoopGroup bossGroup = new NioEventLoopGroup(3);
        EventLoopGroup workerGroup = new NioEventLoopGroup(Runtime.getRuntime().availableProcessors());

        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 1024)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .childOption(ChannelOption.SO_RCVBUF, 1048576)
                    .childOption(ChannelOption.SO_SNDBUF, 1048576)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(new HttpRequestDecoder());
                            ch.pipeline().addLast(new HttpServerCodec());
                            ch.pipeline().addLast(new HttpObjectAggregator(10485760));
                            ch.pipeline().addLast(new IdleStateHandler(0, 0, 60, TimeUnit.SECONDS));
                            ch.pipeline().addLast(new SimpleHttpHandler());
                            ch.pipeline().addLast(new ExceptionHandler());
                        }
                    });

            ChannelFuture f = b.bind(port).sync();
            System.out.println("Server started on port " + port);
            f.channel().closeFuture().sync();

        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

    private class SimpleHttpHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

        private final ObjectMapper objectMapper = new ObjectMapper();
        private static final HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest req) {
            try {

                // Acquire the semaphore to ensure only one request is processed at a time
                semaphore.acquire();

                String uri = req.uri();
                HttpMethod method = req.method();
                String path = URI.create(uri).getPath();

                // Check if the path is not "/api/verve/accept" or method is not GET
                if (!"/api/verve/accept".equals(path)) {
                    sendErrorResponse(ctx, HttpResponseStatus.NOT_FOUND, "Endpoint not found");
                    return;
                }

                if (!HttpMethod.GET.equals(method)) {
                    sendErrorResponse(ctx, HttpResponseStatus.METHOD_NOT_ALLOWED, "Only GET method is allowed");
                    return;
                }

                // Process valid GET request for /api/verve/accept
                handleGetRequest(ctx, req);

            } catch (Exception e) {
                handleException(ctx, e);
            } finally {
                semaphore.release();
            }
        }

        private void handleGetRequest(ChannelHandlerContext ctx, FullHttpRequest req) {
            String idStr = getQueryParameter(req, "id");
            String endpoint = getQueryParameter(req, "endpoint");

            if (idStr == null) {
                sendErrorResponse(ctx, HttpResponseStatus.BAD_REQUEST, "Missing mandatory 'id' query parameter");
                return;
            }

            try {
                int id = Integer.parseInt(idStr);

                // Use Redis to track unique 'id' values
                if (jedis.sismember("unique_ids", idStr)) {
                    sendErrorResponse(ctx, HttpResponseStatus.BAD_REQUEST, "Duplicate 'id' detected");
                    return;
                }

                jedis.sadd("unique_ids", idStr);
                jedis.incr("unique_id_count");

                if (endpoint != null) {
                    if (!isValidEndpoint(endpoint)) {
                        sendErrorResponse(ctx, HttpResponseStatus.BAD_REQUEST, "Invalid 'endpoint' query parameter");
                        return;
                    }
                    // Get the current unique id count from Redis
                    String countStr = jedis.get("unique_id_count");
                    int uniqueCount = (countStr != null) ? Integer.parseInt(countStr) : 0;

                    // Send the unique count to the provided endpoint
                    sendPostRequest(endpoint, uniqueCount);
                }

                sendSuccessResponse(ctx, "ok");

            } catch (NumberFormatException e) {
                sendErrorResponse(ctx, HttpResponseStatus.BAD_REQUEST, "Invalid 'id' query parameter, must be an integer");
            }
        }

        private String getQueryParameter(FullHttpRequest req, String param) {
            URI uri = URI.create(req.uri());
            String query = uri.getQuery();
            if (query != null) {
                for (String pair : query.split("&")) {
                    String[] keyValue = pair.split("=");
                    if (keyValue.length == 2 && keyValue[0].equals(param)) return keyValue[1];
                }
            }
            return null;
        }

        private void sendPostRequest(String endpoint, int uniqueCount) {
            // Check if the endpoint has a valid scheme (http:// or https://)
            if (!endpoint.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*$")) endpoint = "http://" + endpoint;
            String url = endpoint + "?uniqueRequestCount=" + uniqueCount;

            // Create the HTTP POST request with a timeout for the response (15 seconds)
            java.net.http.HttpRequest postRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .timeout(Duration.ofSeconds(15))
                    .build();

            // Send the request asynchronously and handle the response
            httpClient.sendAsync(postRequest, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        int statusCode = response.statusCode();
                        logger.info("POST request to " + url + " returned status code: " + statusCode);
                        return response;
                    }).exceptionally(ex -> {
                        // Handle exceptions (like timeouts or connection failures) and log them
                        if (ex instanceof java.net.ConnectException) logger.severe("Connection failed: Unable to reach " + url);
                        else if (ex instanceof java.net.http.HttpTimeoutException) logger.severe("Timeout while sending POST request to " + url);
                        else logger.severe("Unexpected error sending POST request to " + url + ": " + ex.getMessage());
                        return null;
                    });
        }

        private boolean isValidEndpoint(String endpoint) {
            try {
                if (!endpoint.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*$")) endpoint = "http://" + endpoint;
                URI uri = new URI(endpoint);
                return uri.isAbsolute() && (uri.getScheme().equals("http") || uri.getScheme().equals("https"));
            } catch (Exception e) {
                return false;
            }
        }

        private void sendErrorResponse(ChannelHandlerContext ctx, HttpResponseStatus status, String message) {
            sendJsonResponse(ctx, status, message);
        }

        private void sendSuccessResponse(ChannelHandlerContext ctx, String message) {
            sendJsonResponse(ctx, HttpResponseStatus.OK, message);
        }

        private void sendJsonResponse(ChannelHandlerContext ctx, HttpResponseStatus status, String message) {
            ObjectNode responseObject = objectMapper.createObjectNode();
            responseObject.put("status", message.equals("ok") ? "success" : "failed");
            responseObject.put("message", message);

            FullHttpResponse response = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1, status,
                    Unpooled.wrappedBuffer(responseObject.toString().getBytes(CharsetUtil.UTF_8))
            );

            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json");
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);  // Keep connection open

            ctx.writeAndFlush(response);
        }

        private void handleException(ChannelHandlerContext ctx, Exception e) {
            e.printStackTrace();
            sendErrorResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR, "Internal Server Error: " + e.getMessage());
        }
    }

    public static class ExceptionHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            cause.printStackTrace();
            ctx.close();
        }
    }
}
