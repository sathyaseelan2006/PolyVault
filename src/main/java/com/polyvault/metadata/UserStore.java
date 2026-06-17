package com.polyvault.metadata;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class UserStore {
    private final Path usersPath;
    private final Map<String, UserRecord> users = new LinkedHashMap<>();

    public record UserRecord(String username, String passwordHash, String salt, String createdAt) {}

    public UserStore(Path metadataDirectory) {
        this.usersPath = metadataDirectory.resolve("users.tsv");
    }

    public synchronized void initialize() throws IOException {
        if (!Files.exists(usersPath)) {
            Files.createFile(usersPath);
            return;
        }
        loadUsers();
    }

    private void loadUsers() throws IOException {
        for (String line : Files.readAllLines(usersPath, StandardCharsets.UTF_8)) {
            if (line.isBlank()) {
                continue;
            }
            String[] parts = line.split("\t", -1);
            if (parts.length >= 4) {
                UserRecord record = new UserRecord(parts[0], parts[1], parts[2], parts[3]);
                users.put(record.username().toLowerCase(), record);
            }
        }
    }

    private synchronized void saveUsers() throws IOException {
        List<String> lines = new ArrayList<>();
        for (UserRecord record : users.values()) {
            lines.add(String.join("\t",
                    record.username(),
                    record.passwordHash(),
                    record.salt(),
                    record.createdAt()
            ));
        }
        Files.write(usersPath, lines, StandardCharsets.UTF_8);
    }

    public synchronized boolean register(String username, String password) throws IOException {
        String key = username.trim().toLowerCase();
        if (key.isEmpty() || users.containsKey(key)) {
            return false;
        }
        String salt = generateSalt();
        String hash = hashPassword(password, salt);
        String now = Instant.now().toString();
        UserRecord record = new UserRecord(username.trim(), hash, salt, now);
        users.put(key, record);
        saveUsers();
        return true;
    }

    public synchronized boolean verify(String username, String password) {
        String key = username.trim().toLowerCase();
        UserRecord record = users.get(key);
        if (record == null) {
            return false;
        }
        String hash = hashPassword(password, record.salt());
        return record.passwordHash().equals(hash);
    }

    private String generateSalt() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private String hashPassword(String password, String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt.getBytes(StandardCharsets.UTF_8));
            byte[] hashed = md.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hashed) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 algorithm missing", e);
        }
    }
}
