package com.polyvault.metadata;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ShareStore {
    private final Path sharesPath;
    private final List<ShareRecord> shares = new ArrayList<>();

    public record ShareRecord(String ownerUid, String ownerEmail, int workspaceNodeId, String peerEmail, String role, String createdAt) {}

    public ShareStore(Path dataDirectory) {
        this.sharesPath = dataDirectory.resolve("shares.tsv");
    }

    public synchronized void initialize() throws IOException {
        if (!Files.exists(sharesPath)) {
            Files.createFile(sharesPath);
            return;
        }
        loadShares();
    }

    private void loadShares() throws IOException {
        shares.clear();
        for (String line : Files.readAllLines(sharesPath, StandardCharsets.UTF_8)) {
            if (line.isBlank()) {
                continue;
            }
            String[] parts = line.split("\t", -1);
            if (parts.length >= 6) {
                try {
                    ShareRecord record = new ShareRecord(
                            parts[0],
                            parts[1],
                            Integer.parseInt(parts[2]),
                            parts[3].trim().toLowerCase(),
                            parts[4],
                            parts[5]
                    );
                    shares.add(record);
                } catch (NumberFormatException ignored) {}
            }
        }
    }

    private synchronized void saveShares() throws IOException {
        List<String> lines = new ArrayList<>();
        for (ShareRecord record : shares) {
            lines.add(String.join("\t",
                    record.ownerUid(),
                    record.ownerEmail(),
                    String.valueOf(record.workspaceNodeId()),
                    record.peerEmail(),
                    record.role(),
                    record.createdAt()
            ));
        }
        Files.write(sharesPath, lines, StandardCharsets.UTF_8);
    }

    public synchronized void addShare(String ownerUid, String ownerEmail, int workspaceNodeId, String peerEmail, String role) throws IOException {
        String email = peerEmail.trim().toLowerCase();
        boolean exists = shares.stream().anyMatch(s -> 
            s.ownerUid().equals(ownerUid) && 
            s.workspaceNodeId() == workspaceNodeId && 
            s.peerEmail().equals(email)
        );
        if (!exists) {
            shares.add(new ShareRecord(ownerUid, ownerEmail, workspaceNodeId, email, role, Instant.now().toString()));
            saveShares();
        }
    }

    public synchronized void removeShare(String ownerUid, int workspaceNodeId, String peerEmail) throws IOException {
        String email = peerEmail.trim().toLowerCase();
        boolean removed = shares.removeIf(s -> 
            s.ownerUid().equals(ownerUid) && 
            s.workspaceNodeId() == workspaceNodeId && 
            s.peerEmail().equals(email)
        );
        if (removed) {
            saveShares();
        }
    }

    public synchronized List<ShareRecord> getSharesForPeer(String peerEmail) {
        if (peerEmail == null) return new ArrayList<>();
        String email = peerEmail.trim().toLowerCase();
        return shares.stream()
                .filter(s -> s.peerEmail().equals(email))
                .collect(Collectors.toList());
    }

    public synchronized List<ShareRecord> getSharesByOwner(String ownerUid) {
        if (ownerUid == null) return new ArrayList<>();
        return shares.stream()
                .filter(s -> s.ownerUid().equals(ownerUid))
                .collect(Collectors.toList());
    }
}
