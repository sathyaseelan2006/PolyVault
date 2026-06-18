package com.polyvault.graph;

import com.polyvault.metadata.FileVersion;
import com.polyvault.metadata.MetadataStore;
import com.polyvault.metadata.UserStore;
import com.polyvault.metadata.VaultNode;
import com.polyvault.storage.StoredFile;
import com.polyvault.storage.StorageService;
import com.polyvault.util.Json;
import com.polyvault.metadata.ShareStore;
import com.polyvault.metadata.ShareStore.ShareRecord;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

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
    private final ShareStore shareStore;
    private final Map<String, UserSessionInfo> sessionTokenToUser = new ConcurrentHashMap<>();
    private final String firebaseApiKey = com.polyvault.server.ServerConfig.getInstance().firebaseApiKey();
    private final String firebaseProjectId = com.polyvault.server.ServerConfig.getInstance().firebaseProjectId();

    // User-specific storage and metadata stores for multi-tenancy
    private final Map<String, MetadataStore> userMetadataStores = new ConcurrentHashMap<>();
    private final Map<String, StorageService> userStorageServices = new ConcurrentHashMap<>();

    public static class UserSessionInfo {
        public final String userId;
        public final String email;
        public UserSessionInfo(String userId, String email) {
            this.userId = userId;
            this.email = email;
        }
    }

    public GraphApiServer(int port, MetadataStore metadataStore, StorageService storageService, UserStore userStore, ShareStore shareStore) {
        this.port = port;
        this.metadataStore = metadataStore;
        this.storageService = storageService;
        this.userStore = userStore;
        this.shareStore = shareStore;
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
            UserSessionInfo session = validateAndGetSession(exchange);
            if (session == null) return;
            MetadataStore userMetaStore = getMetadataStoreFor(session.userId);
            
            List<ShareStore.ShareRecord> peerShares = shareStore.getSharesForPeer(session.email);
            List<GraphService.SharedMetadataStoreInfo> sharedStores = new ArrayList<>();
            for (int i = 0; i < peerShares.size(); i++) {
                ShareStore.ShareRecord share = peerShares.get(i);
                MetadataStore ownerMetaStore = getMetadataStoreFor(share.ownerUid());
                sharedStores.add(new GraphService.SharedMetadataStoreInfo(i + 1, ownerMetaStore, share.workspaceNodeId(), share.ownerEmail()));
            }
            
            writeJson(exchange, 200, new GraphService(userMetaStore).combinedGraphJson(sharedStores));
        });
        
        server.createContext("/api/list", exchange -> {
            if (handleOptions(exchange)) return;
            UserSessionInfo session = validateAndGetSession(exchange);
            if (session == null) return;
            Map<String, String> query = query(exchange.getRequestURI().getRawQuery());
            int parentId = Integer.parseInt(query.getOrDefault("parentId", "0"));

            if (parentId >= 10000000) {
                int shareIndex = parentId / 10000000;
                int localParentId = parentId % 10000000;
                
                List<ShareStore.ShareRecord> activeShares = shareStore.getSharesForPeer(session.email);
                if (shareIndex <= 0 || shareIndex > activeShares.size()) {
                    writeJson(exchange, 403, "{\"error\":\"Access denied\"}");
                    return;
                }
                ShareStore.ShareRecord record = activeShares.get(shareIndex - 1);
                MetadataStore sharedMetaStore = getMetadataStoreFor(record.ownerUid());
                writeJson(exchange, 200, sharedChildrenJson(sharedMetaStore, localParentId, shareIndex));
                return;
            }

            if (parentId == 0) {
                MetadataStore userMetaStore = getMetadataStoreFor(session.userId);
                List<ShareStore.ShareRecord> activeShares = shareStore.getSharesForPeer(session.email);
                if (activeShares.isEmpty()) {
                    writeJson(exchange, 200, userMetaStore.childrenJson(0));
                    return;
                }
                
                StringBuilder sb = new StringBuilder("{\"parentId\":0,\"nodes\":[");
                boolean first = true;
                for (VaultNode node : userMetaStore.allNodes()) {
                    if (node.parentId() == 0) {
                        if (!first) sb.append(",");
                        appendChildrenNodeJson(sb, node, 0, false);
                        first = false;
                    }
                }
                for (int i = 0; i < activeShares.size(); i++) {
                    ShareStore.ShareRecord share = activeShares.get(i);
                    MetadataStore ownerMetaStore = getMetadataStoreFor(share.ownerUid());
                    VaultNode sharedRoot = ownerMetaStore.getNode(share.workspaceNodeId());
                    if (sharedRoot != null) {
                        if (!first) sb.append(",");
                        int offset = (i + 1) * 10000000;
                        appendChildrenNodeJson(sb, sharedRoot, offset, true);
                        first = false;
                    }
                }
                sb.append("],\"files\":[");
                first = true;
                for (com.polyvault.metadata.FileRecord file : userMetaStore.allFiles()) {
                    VaultNode node = userMetaStore.getNode(file.nodeId());
                    if (!file.deleted() && node != null && node.parentId() == 0) {
                        if (!first) sb.append(",");
                        appendChildrenFileJson(sb, file, 0);
                        first = false;
                    }
                }
                sb.append("]}");
                writeJson(exchange, 200, sb.toString());
                return;
            }

            MetadataStore userMetaStore = getMetadataStoreFor(session.userId);
            writeJson(exchange, 200, userMetaStore.childrenJson(parentId));
        });
        
        server.createContext("/api/nodes", exchange -> {
            if (handleOptions(exchange)) return;
            UserSessionInfo session = validateAndGetSession(exchange);
            if (session == null) return;
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                Map<String, String> query = query(exchange.getRequestURI().getRawQuery());
                int parentId = Integer.parseInt(query.getOrDefault("parentId", "0"));
                if (parentId >= 10000000) {
                    writeJson(exchange, 403, "{\"error\":\"Cannot modify read-only shared workspace\"}");
                    return;
                }
                MetadataStore userMetaStore = getMetadataStoreFor(session.userId);
                VaultNode node = userMetaStore.createNode(parentId, query.getOrDefault("type", "FOLDER"), query.getOrDefault("title", "Untitled"));
                writeJson(exchange, 200, "{\"nodeId\":" + node.id() + ",\"message\":\"Node created\"}");
                return;
            }
            if ("DELETE".equalsIgnoreCase(exchange.getRequestMethod())) {
                Map<String, String> query = query(exchange.getRequestURI().getRawQuery());
                int nodeId = Integer.parseInt(query.get("nodeId"));
                if (nodeId >= 10000000) {
                    writeJson(exchange, 403, "{\"error\":\"Cannot modify read-only shared workspace\"}");
                    return;
                }
                MetadataStore userMetaStore = getMetadataStoreFor(session.userId);
                userMetaStore.deleteNode(nodeId);
                writeJson(exchange, 200, "{\"message\":\"Node deleted recursively\"}");
                return;
            }
            writeJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
        });
        
        server.createContext("/api/nodes/update", exchange -> {
            if (handleOptions(exchange)) return;
            UserSessionInfo session = validateAndGetSession(exchange);
            if (session == null) return;
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                Map<String, String> query = query(exchange.getRequestURI().getRawQuery());
                int nodeId = Integer.parseInt(query.get("nodeId"));
                if (nodeId >= 10000000) {
                    writeJson(exchange, 403, "{\"error\":\"Cannot modify read-only shared workspace\"}");
                    return;
                }
                MetadataStore userMetaStore = getMetadataStoreFor(session.userId);
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
            UserSessionInfo session = validateAndGetSession(exchange);
            if (session == null) return;
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                writeJson(exchange, 405, "{\"error\":\"POST required\"}");
                return;
            }
            Map<String, String> query = query(exchange.getRequestURI().getRawQuery());
            int parentId = Integer.parseInt(query.getOrDefault("parentId", "0"));
            if (parentId >= 10000000) {
                writeJson(exchange, 403, "{\"error\":\"Cannot upload to read-only shared workspace\"}");
                return;
            }
            MetadataStore userMetaStore = getMetadataStoreFor(session.userId);
            StorageService userStoreService = getStorageServiceFor(session.userId, userMetaStore);
            byte[] body = exchange.getRequestBody().readAllBytes();
            StoredFile stored = userStoreService.store(
                    parentId,
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
            UserSessionInfo session = validateAndGetSession(exchange);
            if (session == null) return;
            Map<String, String> query = query(exchange.getRequestURI().getRawQuery());
            int fileId = Integer.parseInt(query.get("fileId"));
            String versionValue = query.getOrDefault("version", "latest");
            
            MetadataStore activeMetaStore;
            StorageService activeStoreService;
            int localFileId;
            
            if (fileId >= 10000000) {
                int shareIndex = fileId / 10000000;
                localFileId = fileId % 10000000;
                
                List<ShareStore.ShareRecord> activeShares = shareStore.getSharesForPeer(session.email);
                if (shareIndex <= 0 || shareIndex > activeShares.size()) {
                    writeJson(exchange, 403, "{\"error\":\"Access denied\"}");
                    return;
                }
                ShareStore.ShareRecord record = activeShares.get(shareIndex - 1);
                activeMetaStore = getMetadataStoreFor(record.ownerUid());
                activeStoreService = getStorageServiceFor(record.ownerUid(), activeMetaStore);
            } else {
                localFileId = fileId;
                activeMetaStore = getMetadataStoreFor(session.userId);
                activeStoreService = getStorageServiceFor(session.userId, activeMetaStore);
            }
            
            FileVersion version = "latest".equalsIgnoreCase(versionValue)
                    ? activeMetaStore.latestVersion(localFileId)
                    : activeMetaStore.version(localFileId, Integer.parseInt(versionValue));
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            
            try {
                activeStoreService.writeDecompressedBody(version, body);
            } catch (SecurityException se) {
                LOGGER.log(Level.WARNING, "Directory traversal blocked: {0}", se.getMessage());
                writeJson(exchange, 403, "{\"error\":\"Forbidden: directory traversal blocked\"}");
                return;
            }
            
            byte[] bytes = body.toByteArray();

            com.polyvault.metadata.FileRecord fileRecord = activeMetaStore.allFiles().stream()
                    .filter(f -> f.id() == localFileId)
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

        server.createContext("/api/share", exchange -> {
            if (handleOptions(exchange)) return;
            UserSessionInfo session = validateAndGetSession(exchange);
            if (session == null) return;
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                writeJson(exchange, 405, "{\"error\":\"POST required\"}");
                return;
            }
            Map<String, String> query = query(exchange.getRequestURI().getRawQuery());
            int nodeId = Integer.parseInt(query.get("nodeId"));
            String email = query.get("email").trim().toLowerCase();
            
            MetadataStore userMetaStore = getMetadataStoreFor(session.userId);
            if (userMetaStore.getNode(nodeId) == null) {
                writeJson(exchange, 403, "{\"error\":\"Access denied: workspace not found\"}");
                return;
            }
            
            shareStore.addShare(session.userId, session.email, nodeId, email, "viewer");
            writeJson(exchange, 200, "{\"message\":\"Workspace shared successfully\"}");
        });

        server.createContext("/api/shares", exchange -> {
            if (handleOptions(exchange)) return;
            UserSessionInfo session = validateAndGetSession(exchange);
            if (session == null) return;
            Map<String, String> query = query(exchange.getRequestURI().getRawQuery());
            int nodeId = Integer.parseInt(query.get("nodeId"));
            
            MetadataStore userMetaStore = getMetadataStoreFor(session.userId);
            if (userMetaStore.getNode(nodeId) == null) {
                writeJson(exchange, 403, "{\"error\":\"Access denied\"}");
                return;
            }
            
            List<ShareStore.ShareRecord> records = shareStore.getSharesByOwner(session.userId).stream()
                    .filter(r -> r.workspaceNodeId() == nodeId)
                    .collect(Collectors.toList());
            
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < records.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append("{\"email\":\"").append(Json.escape(records.get(i).peerEmail())).append("\",")
                        .append("\"createdAt\":\"").append(Json.escape(records.get(i).createdAt())).append("\"}");
            }
            sb.append("]");
            writeJson(exchange, 200, sb.toString());
        });

        server.createContext("/api/share/revoke", exchange -> {
            if (handleOptions(exchange)) return;
            UserSessionInfo session = validateAndGetSession(exchange);
            if (session == null) return;
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                writeJson(exchange, 405, "{\"error\":\"POST required\"}");
                return;
            }
            Map<String, String> query = query(exchange.getRequestURI().getRawQuery());
            int nodeId = Integer.parseInt(query.get("nodeId"));
            String email = query.get("email").trim().toLowerCase();
            
            MetadataStore userMetaStore = getMetadataStoreFor(session.userId);
            if (userMetaStore.getNode(nodeId) == null) {
                writeJson(exchange, 403, "{\"error\":\"Access denied\"}");
                return;
            }
            
            shareStore.removeShare(session.userId, nodeId, email);
            writeJson(exchange, 200, "{\"message\":\"Share revoked successfully\"}");
        });
        
        server.start();
        LOGGER.log(Level.INFO, "PolyVault web API listening on http://localhost:{0}/api/graph", String.valueOf(port));
    }

    private UserSessionInfo validateAndGetSession(HttpExchange exchange) throws IOException {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            UserSessionInfo cached = sessionTokenToUser.get(token);
            if (cached != null) {
                return cached;
            }
            UserSessionInfo verified = verifyFirebaseToken(token);
            if (verified != null) {
                sessionTokenToUser.put(token, verified);
                return verified;
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

    private UserSessionInfo verifyFirebaseToken(String token) {
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
                String uid = extractJsonField(responseBody, "localId");
                String email = extractJsonField(responseBody, "email");
                return new UserSessionInfo(uid, email);
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

    private String sharedChildrenJson(MetadataStore sharedMetaStore, int localParentId, int shareIndex) {
        int offset = shareIndex * 10000000;
        StringBuilder json = new StringBuilder();
        int displayParentId = localParentId == 0 ? 0 : localParentId + offset;
        json.append("{\"parentId\":").append(displayParentId).append(",\"nodes\":[");
        boolean first = true;
        for (VaultNode node : sharedMetaStore.allNodes()) {
            if (node.parentId() == localParentId) {
                if (!first) json.append(',');
                int dispNodeId = node.id() + offset;
                int dispParentId = node.parentId() == 0 ? 0 : node.parentId() + offset;
                json.append("{\"id\":").append(dispNodeId)
                        .append(",\"parentId\":").append(dispParentId)
                        .append(",\"type\":\"").append(Json.escape(node.type()))
                        .append("\",\"title\":\"").append(Json.escape(node.title()))
                        .append("\",\"updatedAt\":\"").append(Json.escape(node.updatedAt()))
                        .append("\",\"readOnly\":true}");
                first = false;
            }
        }
        json.append("],\"files\":[");
        first = true;
        for (com.polyvault.metadata.FileRecord file : sharedMetaStore.allFiles()) {
            VaultNode node = sharedMetaStore.getNode(file.nodeId());
            if (node != null && node.parentId() == localParentId) {
                if (!first) json.append(',');
                int dispFileId = file.id() + offset;
                int dispNodeId = file.nodeId() + offset;
                json.append("{\"id\":").append(dispFileId)
                        .append(",\"nodeId\":").append(dispNodeId)
                        .append(",\"filename\":\"").append(Json.escape(file.originalFilename()))
                        .append("\",\"type\":\"").append(Json.escape(file.fileType()))
                        .append("\",\"currentVersion\":").append(file.currentVersion())
                        .append("\",\"updatedAt\":\"").append(Json.escape(file.updatedAt()))
                        .append("\",\"readOnly\":true}");
                first = false;
            }
        }
        json.append("]}");
        return json.toString();
    }

    private void appendChildrenNodeJson(StringBuilder sb, VaultNode node, int offset, boolean readOnly) {
        sb.append("{\"id\":").append(node.id() + offset)
                .append(",\"parentId\":0")
                .append(",\"type\":\"").append(Json.escape(node.type()))
                .append("\",\"title\":\"").append(Json.escape(node.title()))
                .append("\",\"updatedAt\":\"").append(Json.escape(node.updatedAt()))
                .append("\",\"readOnly\":").append(readOnly).append("}");
    }

    private void appendChildrenFileJson(StringBuilder sb, com.polyvault.metadata.FileRecord file, int offset) {
        sb.append("{\"id\":").append(file.id() + offset)
                .append(",\"nodeId\":").append(file.nodeId() + offset)
                .append(",\"filename\":\"").append(Json.escape(file.originalFilename()))
                .append("\",\"type\":\"").append(Json.escape(file.fileType()))
                .append("\",\"currentVersion\":").append(file.currentVersion())
                .append("\",\"updatedAt\":\"").append(Json.escape(file.updatedAt())).append("\"}");
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
