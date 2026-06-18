package com.polyvault.metadata;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class InviteStore {
    private final Path invitesPath;
    private final List<InviteRecord> invites = new ArrayList<>();

    public record InviteRecord(String inviteCode, String ownerUid, String ownerEmail, int workspaceNodeId, String createdAt) {}

    public InviteStore(Path dataDirectory) {
        this.invitesPath = dataDirectory.resolve("invites.tsv");
    }

    public synchronized void initialize() throws IOException {
        if (!Files.exists(invitesPath)) {
            Files.createFile(invitesPath);
            return;
        }
        loadInvites();
    }

    private void loadInvites() throws IOException {
        invites.clear();
        for (String line : Files.readAllLines(invitesPath, StandardCharsets.UTF_8)) {
            if (line.isBlank()) {
                continue;
            }
            String[] parts = line.split("\t", -1);
            if (parts.length >= 5) {
                try {
                    InviteRecord record = new InviteRecord(
                            parts[0],
                            parts[1],
                            parts[2],
                            Integer.parseInt(parts[3]),
                            parts[4]
                    );
                    invites.add(record);
                } catch (NumberFormatException ignored) {}
            }
        }
    }

    private synchronized void saveInvites() throws IOException {
        List<String> lines = new ArrayList<>();
        for (InviteRecord record : invites) {
            lines.add(String.join("\t",
                    record.inviteCode(),
                    record.ownerUid(),
                    record.ownerEmail(),
                    String.valueOf(record.workspaceNodeId()),
                    record.createdAt()
            ));
        }
        Files.write(invitesPath, lines, StandardCharsets.UTF_8);
    }

    public synchronized void addInvite(String inviteCode, String ownerUid, String ownerEmail, int workspaceNodeId) throws IOException {
        invites.removeIf(i -> i.ownerUid().equals(ownerUid) && i.workspaceNodeId() == workspaceNodeId);
        
        invites.add(new InviteRecord(inviteCode, ownerUid, ownerEmail, workspaceNodeId, Instant.now().toString()));
        saveInvites();
    }

    public synchronized Optional<InviteRecord> getInvite(String inviteCode) {
        if (inviteCode == null) return Optional.empty();
        return invites.stream()
                .filter(i -> i.inviteCode().equals(inviteCode))
                .findFirst();
    }

    public synchronized Optional<InviteRecord> getInviteForWorkspace(String ownerUid, int workspaceNodeId) {
        if (ownerUid == null) return Optional.empty();
        return invites.stream()
                .filter(i -> i.ownerUid().equals(ownerUid) && i.workspaceNodeId() == workspaceNodeId)
                .findFirst();
    }

    public synchronized void removeInvite(String inviteCode) throws IOException {
        boolean removed = invites.removeIf(i -> i.inviteCode().equals(inviteCode));
        if (removed) {
            saveInvites();
        }
    }
}
