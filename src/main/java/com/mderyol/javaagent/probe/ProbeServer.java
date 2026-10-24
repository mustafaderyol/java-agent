package com.mderyol.javaagent.probe;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mderyol.javaagent.config.AgentConfig;
import com.mderyol.javaagent.logger.AsyncTraceLogger;
import com.mderyol.javaagent.logger.HostInfo;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

public class ProbeServer {
    private static volatile ProbeServer INSTANCE;
    private HttpServer server;
    private int boundPort;
    private final ObjectMapper mapper = new ObjectMapper();
    private AgentConfig config;

    public static synchronized void start(AgentConfig config) {
        if (config.getProbePort() <= 0) return;
        if (INSTANCE == null) {
            INSTANCE = new ProbeServer();
            INSTANCE.config = config;
            INSTANCE.init(config.getProbePort());
        }
    }

    private void init(int preferredPort) {
        int maxAttempts = 20;
        int currentPort = preferredPort;

        for (int i = 0; i < maxAttempts; i++) {
            try {
                server = HttpServer.create(new InetSocketAddress(currentPort), 0);
                boundPort = currentPort;
                break;
            } catch (Exception e) {
                currentPort++;
            }
        }

        if (server == null) {
            System.err.println("[JavaAgent] Could not bind ProbeServer to any port in range [" + preferredPort + " - " + currentPort + "]");
            return;
        }

        // Endpoint routing
        server.createContext("/probe/start", new StartProbeHandler());
        server.createContext("/probe/stop", new StopProbeHandler());
        server.createContext("/probe/status", new StatusProbeHandler());
        server.createContext("/health", new HealthHandler());
        server.createContext("/metrics", new MetricsHandler());
        server.createContext("/env", new EnvHandler());
        server.createContext("/beans", new BeansHandler());

        // Embedded UI and Log Inspection Services
        server.createContext("/ui", new UiHandler());
        server.createContext("/api/files", new FilesHandler());
        server.createContext("/api/logs", new ReadLogsHandler());

        server.setExecutor(Executors.newFixedThreadPool(3, r -> {
            Thread t = new Thread(r, "JavaAgent-ProbeHttpServer");
            t.setDaemon(true);
            return t;
        }));

        server.start();
        System.out.println("[JavaAgent] Dynamic Probe & Trace UI Server started on: http://localhost:" + boundPort + "/ui"
                + " (JVM: " + HostInfo.getJvmId() + ", PID: " + HostInfo.getPid() + ")");
    }

    public int getBoundPort() {
        return boundPort;
    }

    // Serves the embedded HTML single-page dashboard
    private class UiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) {
            try {
                // Read trace-viewer.html from working directory or fallback to JAR classpath
                Path localFile = Paths.get("trace-viewer.html");
                byte[] content = null;
                if (Files.exists(localFile)) {
                    content = Files.readAllBytes(localFile);
                } else {
                    try (InputStream is = getClass().getClassLoader().getResourceAsStream("trace-viewer.html")) {
                        if (is != null) content = is.readAllBytes();
                    }
                }

                if (content == null) {
                    sendResponse(exchange, 404, Map.of("error", "trace-viewer.html not found"));
                    return;
                }

                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                exchange.sendResponseHeaders(200, content.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(content);
                }
            } catch (Exception e) {
                sendResponse(exchange, 500, Map.of("error", e.getMessage()));
            }
        }
    }

    // List log files in the directory for viewer selection
    private class FilesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) {
            try {
                Path dir = Paths.get(config.getLogDirectory());
                List<Map<String, Object>> files = new ArrayList<>();

                if (Files.exists(dir)) {
                    try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
                        for (Path p : stream) {
                            if (Files.isRegularFile(p) && p.getFileName().toString().endsWith(".log") || p.getFileName().toString().contains(".log.")) {
                                Map<String, Object> fileInfo = new LinkedHashMap<>();
                                String name = p.getFileName().toString();
                                fileInfo.put("name", name);
                                fileInfo.put("sizeBytes", Files.size(p));
                                fileInfo.put("sizeFormatted", formatBytes(Files.size(p)));
                                fileInfo.put("lastModified", Files.getLastModifiedTime(p).toInstant().toString());
                                fileInfo.put("isProbe", name.contains("probe"));
                                fileInfo.put("isActive", name.equals(config.getEffectiveTraceFileName()) || name.equals(config.getEffectiveProbeFileName()));
                                files.add(fileInfo);
                            }
                        }
                    }
                }

                // İsme ve son değiştirilmeye göre sırala (aktifler en başta)
                files.sort((a, b) -> ((String) a.get("name")).compareTo((String) b.get("name")));

                addCorsHeaders(exchange);
                Map<String, Object> respMap = new LinkedHashMap<>();
                respMap.put("logDirectory", dir.toAbsolutePath().toString());
                respMap.put("activeTraceFile", config.getEffectiveTraceFileName());
                respMap.put("activeProbeFile", config.getEffectiveProbeFileName());
                respMap.put("jvmId", HostInfo.getJvmId());
                respMap.put("files", files);
                sendResponse(exchange, 200, respMap);
            } catch (Exception e) {
                sendResponse(exchange, 500, Map.of("error", e.getMessage()));
            }
        }

        private String formatBytes(long bytes) {
            if (bytes < 1024) return bytes + " B";
            if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        }
    }

    // Büyük Dosyaları Sunucuda Filtreleyerek Okuma Endpoint'i
    private class ReadLogsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) {
            try {
                addCorsHeaders(exchange);
                Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());

                String fileName = params.getOrDefault("file", config.getEffectiveTraceFileName());
                // Güvenlik: path traversal engelleme
                if (fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")) {
                    sendResponse(exchange, 400, Map.of("error", "Invalid file name"));
                    return;
                }

                int tailLimit = Integer.parseInt(params.getOrDefault("tail", "1000"));
                String methodFilter = params.get("method");
                Double minDuration = params.containsKey("minDuration") ? Double.parseDouble(params.get("minDuration")) : null;

                Path filePath = Paths.get(config.getLogDirectory()).resolve(fileName);
                if (!Files.exists(filePath)) {
                    sendResponse(exchange, 404, Map.of("error", "File not found: " + fileName));
                    return;
                }

                // Dosyayı satır satır tara (Bellek taşması olmadan!)
                List<String> matchedLines = new ArrayList<>();
                try (BufferedReader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String trimmed = line.trim();
                        if (trimmed.isEmpty()) continue;

                        // Metot ve Süre filtresi kontrolü (Hızlı string check)
                        if (methodFilter != null && !methodFilter.isBlank()) {
                            if (!trimmed.toLowerCase().contains(methodFilter.toLowerCase())) {
                                continue;
                            }
                        }

                        if (minDuration != null) {
                            // Hızlı durationMs kontrolü
                            if (!passesDurationFilter(trimmed, minDuration)) {
                                continue;
                            }
                        }

                        matchedLines.add(trimmed);
                        // Son tailLimit kadar tut
                        if (matchedLines.size() > tailLimit * 2) {
                            matchedLines.subList(0, matchedLines.size() - tailLimit).clear();
                        }
                    }
                }

                // Son N tanesini al
                if (matchedLines.size() > tailLimit) {
                    matchedLines = matchedLines.subList(matchedLines.size() - tailLimit, matchedLines.size());
                }

                exchange.getResponseHeaders().set("Content-Type", "application/x-ndjson; charset=UTF-8");
                exchange.sendResponseHeaders(200, 0);
                try (OutputStream os = exchange.getResponseBody();
                     BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8))) {
                    for (String l : matchedLines) {
                        bw.write(l);
                        bw.newLine();
                    }
                    bw.flush();
                }

            } catch (Exception e) {
                sendResponse(exchange, 500, Map.of("error", e.getMessage()));
            }
        }

        private boolean passesDurationFilter(String jsonLine, double minMs) {
            int idx = jsonLine.indexOf("\"durationMs\":");
            if (idx == -1) return true;
            try {
                int start = idx + 13;
                int end = jsonLine.indexOf(",", start);
                if (end == -1) end = jsonLine.indexOf("}", start);
                double dur = Double.parseDouble(jsonLine.substring(start, end).trim());
                return dur >= minMs;
            } catch (Exception e) {
                return true;
            }
        }
    }

    private class StartProbeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) {
            try {
                addCorsHeaders(exchange);
                if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    sendResponse(exchange, 405, Map.of("error", "Method not allowed. Use POST."));
                    return;
                }
                InputStream is = exchange.getRequestBody();
                Map<String, Object> req = mapper.readValue(is, Map.class);

                String className = (String) req.get("className");
                String methodName = (String) req.get("methodName");
                Number durationSecNum = (Number) req.getOrDefault("durationSeconds", 60);
                Number maxCapturesNum = (Number) req.getOrDefault("maxCaptures", 50);

                int durationSeconds = durationSecNum.intValue();
                int maxCaptures = maxCapturesNum.intValue();

                List<Map<String, Object>> probesList = (List<Map<String, Object>>) req.get("probes");
                if (probesList != null && !probesList.isEmpty()) {
                    List<String> results = new ArrayList<>();
                    for (Map<String, Object> p : probesList) {
                        String cn = (String) p.get("className");
                        String mn = (String) p.get("methodName");
                        int dSec = p.containsKey("durationSeconds") ? ((Number) p.get("durationSeconds")).intValue() : durationSeconds;
                        int mCap = p.containsKey("maxCaptures") ? ((Number) p.get("maxCaptures")).intValue() : maxCaptures;
                        String res = DynamicProbeManager.getInstance().registerProbe(cn, mn, dSec, mCap);
                        results.add(res);
                    }
                    Map<String, Object> resp = new LinkedHashMap<>();
                    resp.put("status", "SUCCESS");
                    resp.put("host", HostInfo.getHostName());
                    resp.put("jvmId", HostInfo.getJvmId());
                    resp.put("pid", HostInfo.getPid());
                    resp.put("registeredCount", probesList.size());
                    resp.put("results", results);
                    resp.put("outputFile", config.getLogDirectory() + "/" + config.getEffectiveProbeFileName());
                    sendResponse(exchange, 200, resp);
                    return;
                }

                String result = DynamicProbeManager.getInstance().registerProbe(className, methodName, durationSeconds, maxCaptures);

                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("host", HostInfo.getHostName());
                resp.put("jvmId", HostInfo.getJvmId());
                resp.put("pid", HostInfo.getPid());
                resp.put("result", result);
                resp.put("className", className);
                resp.put("methodName", methodName);
                resp.put("durationSeconds", durationSeconds);
                resp.put("maxCaptures", maxCaptures);
                resp.put("outputFile", config.getLogDirectory() + "/" + config.getEffectiveProbeFileName());

                sendResponse(exchange, 200, resp);
            } catch (Exception e) {
                sendResponse(exchange, 400, Map.of("error", "Invalid payload: " + e.getMessage()));
            }
        }
    }

    private class StopProbeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) {
            try {
                addCorsHeaders(exchange);
                if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    sendResponse(exchange, 405, Map.of("error", "Method not allowed. Use POST."));
                    return;
                }
                InputStream is = exchange.getRequestBody();
                Map<String, Object> req = mapper.readValue(is, Map.class);

                String className = (String) req.get("className");
                String methodName = (String) req.get("methodName");
                Boolean all = (Boolean) req.get("all");

                if (Boolean.TRUE.equals(all) || "*".equals(className)) {
                    DynamicProbeManager.getInstance().clearAll();
                    sendResponse(exchange, 200, Map.of("clearedAll", true, "removed", true));
                    return;
                }

                boolean removed = DynamicProbeManager.getInstance().removeProbe(className, methodName);
                sendResponse(exchange, 200, Map.of("removed", removed, "className", String.valueOf(className), "methodName", String.valueOf(methodName)));
            } catch (Exception e) {
                sendResponse(exchange, 400, Map.of("error", e.getMessage()));
            }
        }
    }

    private class StatusProbeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) {
            addCorsHeaders(exchange);
            Map<String, ProbeRule> active = DynamicProbeManager.getInstance().getActiveRules();
            List<Map<String, Object>> list = new ArrayList<>();

            for (ProbeRule rule : active.values()) {
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("className", rule.getClassName());
                r.put("methodName", rule.getMethodName());
                r.put("remainingSeconds", rule.getRemainingSeconds());
                r.put("remainingCaptures", rule.getRemainingCaptures());
                r.put("totalAllowed", rule.getTotalCapturesAllowed());
                list.add(r);
            }

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("host", HostInfo.getHostName());
            resp.put("jvmId", HostInfo.getJvmId());
            resp.put("activeProbeCount", list.size());
            resp.put("activeProbes", list);

            sendResponse(exchange, 200, resp);
        }
    }

    private class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) {
            addCorsHeaders(exchange);
            AsyncTraceLogger logger = AsyncTraceLogger.getInstance();
            Map<String, Object> metrics = new LinkedHashMap<>();
            metrics.put("status", "UP");
            metrics.put("host", HostInfo.getHostName());
            metrics.put("jvmId", HostInfo.getJvmId());
            metrics.put("pid", HostInfo.getPid());
            metrics.put("probePort", boundPort);
            metrics.put("uiUrl", "http://localhost:" + boundPort + "/ui");
            metrics.put("loggedEvents", logger.getLoggedEventsCount());
            metrics.put("droppedEvents", logger.getDroppedEventsCount());
            metrics.put("queuePendingSize", logger.getQueueSize());
            metrics.put("activeProbes", DynamicProbeManager.getInstance().getActiveRules().size());

            sendResponse(exchange, 200, metrics);
        }
    }

    private class MetricsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) {
            addCorsHeaders(exchange);
            try {
                com.mderyol.javaagent.metrics.AsyncMetricsLogger metricsLogger = com.mderyol.javaagent.metrics.AsyncMetricsLogger.getInstance();
                if (metricsLogger != null) {
                    com.mderyol.javaagent.metrics.SystemMetricsSnapshot snapshot = metricsLogger.getLatestSnapshot();
                    sendResponse(exchange, 200, snapshot.toMap());
                } else {
                    com.mderyol.javaagent.metrics.SystemMetricsSnapshot snapshot = com.mderyol.javaagent.metrics.MetricsCollector.collect(config);
                    sendResponse(exchange, 200, snapshot.toMap());
                }
            } catch (Exception e) {
                sendResponse(exchange, 500, Map.of("error", "Failed to retrieve metrics: " + e.getMessage()));
            }
        }
    }

    private class EnvHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) {
            addCorsHeaders(exchange);
            try {
                Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
                boolean unmask = "true".equalsIgnoreCase(params.get("unmask"));
                boolean mask = !unmask && config.isMaskSensitiveEnv();
                String filterParam = params.get("filter");
                List<String> filters = filterParam != null && !filterParam.isBlank()
                        ? Arrays.asList(filterParam.split(","))
                        : config.getEnvFilter();

                Map<String, Object> envSnapshot = com.mderyol.javaagent.env.EnvironmentManager.getCombinedSnapshot(mask, filters);
                sendResponse(exchange, 200, envSnapshot);
            } catch (Exception e) {
                sendResponse(exchange, 500, Map.of("error", "Failed to retrieve environment: " + e.getMessage()));
            }
        }
    }

    private class BeansHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) {
            addCorsHeaders(exchange);
            try {
                Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
                String nameFilter = params.get("name");
                String packageFilter = params.get("package");

                Map<String, Object> beansSnapshot = com.mderyol.javaagent.beans.SpringBeanManager.getInstance().getBeansSnapshot(nameFilter, packageFilter);
                sendResponse(exchange, 200, beansSnapshot);
            } catch (Exception e) {
                sendResponse(exchange, 500, Map.of("error", "Failed to retrieve Spring beans: " + e.getMessage()));
            }
        }
    }

    private static Map<String, String> parseQueryParams(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null || query.isBlank()) return map;
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                map.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                        URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
            }
        }
        return map;
    }

    private void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
    }

    private void sendResponse(HttpExchange exchange, int statusCode, Object data) {
        try {
            addCorsHeaders(exchange);
            byte[] bytes = mapper.writeValueAsBytes(data);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(statusCode, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        } catch (Exception ignored) {}
    }
}
