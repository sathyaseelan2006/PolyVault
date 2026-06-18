package com.polyvault.graph;

import com.polyvault.metadata.FileVersion;
import com.polyvault.metadata.MetadataStore;
import com.polyvault.metadata.UserStore;
import com.polyvault.metadata.VaultNode;
import com.polyvault.storage.StoredFile;
import com.polyvault.storage.StorageService;
import com.polyvault.util.Json;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GraphApiServer {
    private static final Logger LOGGER = Logger.getLogger(GraphApiServer.class.getName());

    private final int port;
    private final MetadataStore metadataStore;
    private final StorageService storageService;
    private final UserStore userStore;
    private final Map<String, String> sessionTokenToUser = new ConcurrentHashMap<>();
    private final String firebaseApiKey = com.polyvault.server.ServerConfig.getInstance().firebaseApiKey();
    private final String firebaseProjectId = com.polyvault.server.ServerConfig.getInstance().firebaseProjectId();

    // User-specific storage and metadata stores for multi-tenancy
    private final Map<String, MetadataStore> userMetadataStores = new ConcurrentHashMap<>();
    private final Map<String, StorageService> userStorageServices = new ConcurrentHashMap<>();

    public GraphApiServer(int port, MetadataStore metadataStore, StorageService storageService, UserStore userStore) {
        this.port = port;
        this.metadataStore = metadataStore;
        this.storageService = storageService;
        this.userStore = userStore;
    }

    public void start() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        
        server.createContext("/", exchange -> {
            if (handleOptions(exchange)) return;
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/")) {
                path = "/index.html";
            }
            if (path.contains("..")) {
                writeJson(exchange, 403, "{\"error\":\"Access denied\"}");
                return;
            }
            Path file = Path.of("frontend").resolve(path.substring(1)).normalize().toAbsolutePath();
            Path frontendDirAbs = Path.of("frontend").toAbsolutePath().normalize();
            if (!file.startsWith(frontendDirAbs) || !Files.exists(file) || Files.isDirectory(file)) {
                writeJson(exchange, 404, "{\"error\":\"File not found: " + path + "\"}");
                return;
            }
            byte[] bytes = Files.readAllBytes(file);
            String contentType = "text/html; charset=utf-8";
            String lower = file.toString().toLowerCase();
            if (lower.endsWith(".css")) {
                contentType = "text/css; charset=utf-8";
            } else if (lower.endsWith(".js")) {
                contentType = "application/javascript; charset=utf-8";
            } else if (lower.endsWith(".png")) {
                contentType = "image/png";
            } else if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
                contentType = "image/jpeg";
            } else if (lower.endsWith(".svg")) {
                contentType = "image/svg+xml";
            } else if (lower.endsWith(".ico")) {
                contentType = "image/x-icon";
            }
            exchange.getResponseHeaders().add("Content-Type", contentType);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });



        server.createContext("/api/auth/logout", exchange -> {
            if (handleOptions(exchange)) return;
            String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7).trim();
                sessionTokenToUser.remove(token);
            }
            writeJson(exchange, 200, "{\"message\":\"Logged out successfully\"}");
        });

        server.createContext("/api/graph", exchange -> {
            if (handleOptions(exchange)) return;
            String userId = validateAndGetUserId(exchange);
            if (userId == null) return;
            MetadataStore userMetaStore = getMetadataStoreFor(userId);
            writeJson(exchange, 200, new GraphService(userMetaStore).graphJson());
        });
        
        server.createContext("/api/list", exchange -> {
            if (handleOptions(exchange)) return;
            String userId = validateAndGetUserId(exchange);
            if (userId == null) return;
            MetadataStore userMetaStore = getMetadataStoreFor(userId);
            Map<String, String> query = query(exchange.getRequestURI().getRawQuery());
            int parentId = Integer.parseInt(query.getOrDefault("parentId", "0"));
            writeJson(exchange, 200, userMetaStore.childrenJson(parentId));
        });
        
        server.createContext("/api/nodes", exchange -> {
            if (handleOptions(exchange)) return;
            String userId = validateAndGetUserId(exchange);
            if (userId == null) return;
            MetadataStore userMetaStore = getMetadataStoreFor(userId);
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                Map<String, String> query = query(exchange.getRequestURI().getRawQuery());
                int parentId = Integer.parseInt(query.getOrDefault("parentId", "0"));
                VaultNode node = userMetaStore.createNode(parentId, query.getOrDefault("type", "FOLDER"), query.getOrDefault("title", "Untitled"));
                writeJson(exchange, 200, "{\"nodeId\":" + node.id() + ",\"message\":\"Node created\"}");
                return;
            }
            if ("DELETE".equalsIgnoreCase(exchange.getRequestMethod())) {
                Map<String, String> query = query(exchange.getRequestURI().getRawQuery());
                int nodeId = Integer.parseInt(query.get("nodeId"));
                userMetaStore.deleteNode(nodeId);
                writeJson(exchange, 200, "{\"message\":\"Node deleted recursively\"}");
                return;
            }
            writeJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
        });
        
        server.createContext("/api/nodes/update", exchange -> {
            if (handleOptions(exchange)) return;
            String userId = validateAndGetUserId(exchange);
            if (userId == null) return;
            MetadataStore userMetaStore = getMetadataStoreFor(userId);
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                Map<String, String> query = query(exchange.getRequestURI().getRawQuery());
                int nodeId = Integer.parseInt(query.get("nodeId"));
                String color = query.getOrDefault("color", "");
                String shape = query.getOrDefault("shape", "circle");
                boolean important = Boolean.parseBoolean(query.getOrDefault("important", "false"));
                boolean favorite = Boolean.parseBoolean(query.getOrDefault("favorite", "false"));
                String shortcut = query.getOrDefault("shortcut", "");
                
                userMetaStore.updateNodeCustomization(nodeId, color, shape, important, favorite, shortcut);
                writeJson(exchange, 200, "{\"nodeId\":" + nodeId + ",\"message\":\"Node customization updated\"}");
                return;
            }
            writeJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
        });
        
        server.createContext("/api/upload", exchange -> {
            if (handleOptions(exchange)) return;
            String userId = validateAndGetUserId(exchange);
            if (userId == null) return;
            MetadataStore userMetaStore = getMetadataStoreFor(userId);
            StorageService userStoreService = getStorageServiceFor(userId, userMetaStore);
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                writeJson(exchange, 405, "{\"error\":\"POST required\"}");
                return;
            }
            Map<String, String> query = query(exchange.getRequestURI().getRawQuery());
            byte[] body = exchange.getRequestBody().readAllBytes();
            StoredFile stored = userStoreService.store(
                    Integer.parseInt(query.getOrDefault("parentId", "0")),
                    query.getOrDefault("filename", "upload.bin"),
                    query.getOrDefault("title", query.getOrDefault("filename", "Uploaded file")),
                    query.getOrDefault("type", "file"),
                    body.length,
                    new java.io.ByteArrayInputStream(body)
            );
            writeJson(exchange, 200, "{\"fileId\":" + stored.fileId() + ",\"nodeId\":" + stored.nodeId()
                    + ",\"version\":" + stored.versionNumber() + ",\"message\":\"Upload successful\"}");
        });
        
        server.createContext("/api/download", exchange -> {
            if (handleOptions(exchange)) return;
            String userId = validateAndGetUserId(exchange);
            if (userId == null) return;
            MetadataStore userMetaStore = getMetadataStoreFor(userId);
            StorageService userStoreService = getStorageServiceFor(userId, userMetaStore);
            Map<String, String> query = query(exchange.getRequestURI().getRawQuery());
            int fileId = Integer.parseInt(query.get("fileId"));
            String versionValue = query.getOrDefault("version", "latest");
            FileVersion version = "latest".equalsIgnoreCase(versionValue)
                    ? userMetaStore.latestVersion(fileId)
                    : userMetaStore.version(fileId, Integer.parseInt(versionValue));
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            
            try {
                userStoreService.writeDecompressedBody(version, body);
            } catch (SecurityException se) {
                LOGGER.log(Level.WARNING, "Directory traversal blocked: {0}", se.getMessage());
                writeJson(exchange, 403, "{\"error\":\"Forbidden: directory traversal blocked\"}");
                return;
            }
            
            byte[] bytes = body.toByteArray();

            com.polyvault.metadata.FileRecord fileRecord = userMetaStore.allFiles().stream()
                    .filter(f -> f.id() == fileId)
                    .findFirst()
                    .orElse(null);
            String filename = fileRecord != null ? fileRecord.originalFilename() : "download.bin";

            String contentType = "application/octet-stream";
            String lower = filename.toLowerCase();
            if (lower.endsWith(".png")) {
                contentType = "image/png";
            } else if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
                contentType = "image/jpeg";
            } else if (lower.endsWith(".gif")) {
                contentType = "image/gif";
            } else if (lower.endsWith(".pdf")) {
                contentType = "application/pdf";
            } else if (lower.endsWith(".html")) {
                contentType = "text/html; charset=utf-8";
            } else if (lower.endsWith(".txt") || lower.endsWith(".java") || lower.endsWith(".js") || lower.endsWith(".py") || lower.endsWith(".css")) {
                contentType = "text/plain; charset=utf-8";
            } else if (lower.endsWith(".json")) {
                contentType = "application/json; charset=utf-8";
            } else if (lower.endsWith(".svg")) {
                contentType = "image/svg+xml";
            } else if (lower.endsWith(".webp")) {
                contentType = "image/webp";
            }
            exchange.getResponseHeaders().add("Content-Type", contentType);

            boolean forceDownload = "true".equalsIgnoreCase(query.get("download"));
            String dispositionMode = forceDownload ? "attachment" : "inline";
            exchange.getResponseHeaders().add("Content-Disposition", dispositionMode + "; filename=\"" + filename + "\"");

            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        
        server.start();
        LOGGER.log(Level.INFO, "PolyVault web API listening on http://localhost:{0}/api/graph", String.valueOf(port));
    }

    private String validateAndGetUserId(HttpExchange exchange) throws IOException {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            String cachedUid = sessionTokenToUser.get(token);
            if (cachedUid != null) {
                return cachedUid;
            }
            String verifiedUid = verifyFirebaseToken(token);
            if (verifiedUid != null) {
                sessionTokenToUser.put(token, verifiedUid);
                return verifiedUid;
            }
        }
        writeJson(exchange, 401, "{\"error\":\"Unauthorized\"}");
        return null;
    }

    private MetadataStore getMetadataStoreFor(String userId) {
        if (userId == null) return metadataStore; // Fallback to global/admin
        return userMetadataStores.computeIfAbsent(userId, uid -> {
            Path userMetaDir = com.polyvault.server.ServerConfig.getInstance().metadataDirectory()
                    .getParent().resolve("users").resolve(uid).resolve("metadata");
            try {
                Files.createDirectories(userMetaDir);
                MetadataStore ms = new MetadataStore(userMetaDir);
                ms.initialize();
                return ms;
            } catch (Exception e) {
                throw new RuntimeException("Failed to load user metadata store", e);
            }
        });
    }

    private StorageService getStorageServiceFor(String userId, MetadataStore metaStore) {
        if (userId == null) return storageService; // Fallback to global/admin
        return userStorageServices.computeIfAbsent(userId, uid -> {
            Path userStorageDir = com.polyvault.server.ServerConfig.getInstance().storageDirectory()
                    .getParent().resolve("users").resolve(uid).resolve("storage");
            try {
                Files.createDirectories(userStorageDir);
                return new StorageService(userStorageDir, metaStore);
            } catch (Exception e) {
                throw new RuntimeException("Failed to load user storage service", e);
            }
        });
    }

    private String verifyFirebaseToken(String token) {
        if (firebaseApiKey == null || firebaseApiKey.isEmpty() || "YOUR_API_KEY".equals(firebaseApiKey)) {
            LOGGER.log(Level.WARNING, "Firebase API Key is missing or default in config.properties!");
            return null;
        }
        try {
            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            String url = "https://identitytoolkit.googleapis.com/v1/accounts:lookup?key=" + firebaseApiKey;
            String body = "{\"idToken\":\"" + token + "\"}";
            
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body))
                    .build();
            
            java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                String responseBody = response.body();
                return extractJsonField(responseBody, "localId");
            } else {
                LOGGER.log(Level.WARNING, "Firebase token verification failed. Status code: {0}, Response: {1}",
                        new Object[]{response.statusCode(), response.body()});
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error verifying Firebase token", e);
        }
        return null;
    }

    private String extractJsonField(String json, String field) {
        String searchKey = "\"" + field + "\"";
        int startIdx = json.indexOf(searchKey);
        if (startIdx == -1) return "";
        int colonIdx = json.indexOf(":", startIdx + searchKey.length());
        if (colonIdx == -1) return "";
        int quoteStart = json.indexOf("\"", colonIdx);
        if (quoteStart == -1) return "";
        int quoteEnd = json.indexOf("\"", quoteStart + 1);
        if (quoteEnd == -1) return "";
        return json.substring(quoteStart + 1, quoteEnd);
    }

    private boolean handleOptions(HttpExchange exchange) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, GET, DELETE, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, Authorization");
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return true;
        }
        return false;
    }

    private void writeJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private Map<String, String> query(String rawQuery) {
        Map<String, String> values = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) {
            return values;
        }
        for (String pair : rawQuery.split("&")) {
            int index = pair.indexOf('=');
            if (index > 0) {
                values.put(decode(pair.substring(0, index)), decode(pair.substring(index + 1)));
            }
        }
        return values;
    }

    private String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
