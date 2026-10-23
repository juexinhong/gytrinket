package com.gytrinket.observer;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 极简 HTTP/1.1 服务（无第三方依赖，避开 jdk.httpserver 模块解析问题）。
 * 只处理单次请求：GET/POST + 查询串/请求体，响应后关闭连接。
 */
public final class SimpleHttpServer implements Closeable {

    public record Response(int status, String contentType, byte[] body) {
        public static Response text(String text) {
            return new Response(200, "text/plain; charset=utf-8", text.getBytes(StandardCharsets.UTF_8));
        }

        public static Response json(String json) {
            return new Response(200, "application/json; charset=utf-8", json.getBytes(StandardCharsets.UTF_8));
        }

        public static Response error(int status, String message) {
            return new Response(status, "text/plain; charset=utf-8", message.getBytes(StandardCharsets.UTF_8));
        }
    }

    public interface Handler {
        Response handle(Map<String, String> query, String body) throws Exception;
    }

    private final ServerSocket socket;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Map<String, Handler> routes;
    private volatile boolean running = true;

    public SimpleHttpServer(int port, Map<String, Handler> routes) throws IOException {
        this.routes = new HashMap<>(routes);
        this.socket = new ServerSocket();
        this.socket.setReuseAddress(true);
        this.socket.bind(new InetSocketAddress("127.0.0.1", port));
        Thread thread = new Thread(this::acceptLoop, "observer-http-" + port);
        thread.setDaemon(true);
        thread.start();
        ObserverMod.LOGGER.info("[observer] HTTP listening on 127.0.0.1:{}", port);
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket client = socket.accept();
                executor.submit(() -> serve(client));
            } catch (IOException exception) {
                if (running) {
                    ObserverMod.LOGGER.warn("[observer] accept failed: {}", exception.toString());
                }
            }
        }
    }

    private void serve(Socket client) {
        try (Socket socket = client) {
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String requestLine = reader.readLine();
            if (requestLine == null || requestLine.isEmpty()) {
                return;
            }
            String[] parts = requestLine.split(" ");
            String method = parts[0];
            String target = parts.length > 1 ? parts[1] : "/";
            int contentLength = 0;
            String header;
            while ((header = reader.readLine()) != null && !header.isEmpty()) {
                if (header.toLowerCase().startsWith("content-length:")) {
                    contentLength = Integer.parseInt(header.substring(15).trim());
                }
            }
            String body = "";
            if (contentLength > 0) {
                char[] buffer = new char[contentLength];
                int read = 0;
                while (read < contentLength) {
                    int n = reader.read(buffer, read, contentLength - read);
                    if (n < 0) {
                        break;
                    }
                    read += n;
                }
                body = new String(buffer, 0, read);
            }
            String path = target;
            String queryString = "";
            int question = target.indexOf('?');
            if (question >= 0) {
                path = target.substring(0, question);
                queryString = target.substring(question + 1);
            }
            Map<String, String> query = parseQuery(queryString);
            Response response;
            Handler handler = routes.get(path);
            if (handler == null) {
                response = Response.error(404, "not found: " + path);
            } else {
                try {
                    response = handler.handle(query, body);
                } catch (Exception exception) {
                    response = Response.error(500, "error: " + exception);
                }
            }
            byte[] payload = response.body();
            String head = "HTTP/1.1 " + response.status() + " OK\r\n"
                    + "Content-Type: " + response.contentType() + "\r\n"
                    + "Content-Length: " + payload.length + "\r\n"
                    + "Access-Control-Allow-Origin: *\r\n"
                    + "Connection: close\r\n\r\n";
            out.write(head.getBytes(StandardCharsets.US_ASCII));
            out.write(payload);
            out.flush();
        } catch (Exception ignored) {
        }
    }

    private static Map<String, String> parseQuery(String queryString) {
        Map<String, String> query = new HashMap<>();
        if (queryString == null || queryString.isEmpty()) {
            return query;
        }
        for (String pair : queryString.split("&")) {
            int equals = pair.indexOf('=');
            if (equals < 0) {
                query.put(decode(pair), "");
            } else {
                query.put(decode(pair.substring(0, equals)), decode(pair.substring(equals + 1)));
            }
        }
        return query;
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (Exception exception) {
            return value;
        }
    }

    public static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }

    public static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        in.transferTo(out);
        return out.toByteArray();
    }

    @Override
    public void close() {
        running = false;
        try {
            socket.close();
        } catch (Exception ignored) {
        }
        executor.shutdownNow();
    }
}
