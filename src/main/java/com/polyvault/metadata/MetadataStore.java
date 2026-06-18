package com.polyvault.metadata;

import com.polyvault.util.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MetadataStore {
    private final Path directory;
    private final Path nodesPath;
    private final Path filesPath;
    private final Path versionsPath;
    private final Map<Integer, VaultNode> nodes = new LinkedHashMap<>();
    private final Map<Integer, FileRecord> files = new LinkedHashMap<>();
    private final List<FileVersion> versions = new ArrayList<>();
    private int nextNodeId = 1;
    private int nextFileId = 1;
    private int nextVersionId = 1;

    public MetadataStore(Path directory) {
        this.directory = directory;
        this.nodesPath = directory.resolve("nodes.tsv");
        this.filesPath = directory.resolve("files.tsv");
        this.versionsPath = directory.resolve("versions.tsv");
    }

    public synchronized void initialize() throws IOException {
        Files.createDirectories(directory);
        loadNodes();
        loadFiles();
        loadVersions();
    }

    public synchronized VaultNode createNode(int parentId, String type, String title) throws IOException {
        if (parentId != 0 && !nodes.containsKey(parentId)) {
            throw new IllegalArgumentException("Parent node does not exist: " + parentId);
        }
        String now = Instant.now().toString();
        VaultNode node = new VaultNode(nextNodeId++, parentId, type, title, now, now, "", "circle", false, false, "");
        nodes.put(node.id(), node);
        saveNodes();
        return node;
    }

    public synchronized VaultNode updateNodeCustomization(int nodeId, String color, String shape, boolean important, boolean favorite, String shortcut) throws IOException {
        VaultNode old = nodes.get(nodeId);
        if (old == null) {
            throw new IllegalArgumentException("Node not found: " + nodeId);
        }
        VaultNode updated = new VaultNode(
                old.id(), 
                old.parentId(), 
                old.type(), 
                old.title(), 
                old.createdAt(), 
                Instant.now().toString(),
                color,
                shape,
                important,
                favorite,
                shortcut
        );
        nodes.put(nodeId, updated);
        saveNodes();
        return updated;
    }

    public synchronized FileRecord createFile(int nodeId, String filename, String fileType) throws IOException {
        if (!nodes.containsKey(nodeId)) {
            throw new IllegalArgumentException("File node does not exist: " + nodeId);
        }
        String now = Instant.now().toString();
        FileRecord file = new FileRecord(nextFileId++, nodeId, filename, fileType, 0, false, now, now);
        files.put(file.id(), file);
        saveFiles();
        return file;
    }

    public synchronized FileRecord updateCurrentVersion(int fileId, int versionNumber) throws IOException {
        FileRecord old = requiredFile(fileId);
        FileRecord next = new FileRecord(old.id(), old.nodeId(), old.originalFilename(), old.fileType(),
                versionNumber, old.deleted(), old.createdAt(), Instant.now().toString());
        files.put(fileId, next);
        saveFiles();
        return next;
    }

    public synchronized FileVersion addVersion(int fileId, int versionNumber, String storagePath,
                                               long originalSize, long compressedSize, String compression) throws IOException {
        FileVersion version = new FileVersion(nextVersionId++, fileId, versionNumber, storagePath,
                originalSize, compressedSize, compression, Instant.now().toString());
        versions.add(version);
        saveVersions();
        updateCurrentVersion(fileId, versionNumber);
        return version;
    }

    public synchronized int nextVersionNumber(int fileId) {
        return versions.stream()
                .filter(version -> version.fileId() == fileId)
                .map(FileVersion::versionNumber)
                .max(Integer::compareTo)
                .orElse(0) + 1;
    }

    public synchronized FileVersion latestVersion(int fileId) {
        return versions.stream()
                .filter(version -> version.fileId() == fileId)
                .max(Comparator.comparingInt(FileVersion::versionNumber))
                .orElseThrow(() -> new IllegalArgumentException("No versions for file: " + fileId));
    }

    public synchronized FileVersion version(int fileId, int versionNumber) {
        return versions.stream()
                .filter(version -> version.fileId() == fileId && version.versionNumber() == versionNumber)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Version not found"));
    }

    public synchronized void markFileDeleted(int fileId) throws IOException {
        FileRecord old = requiredFile(fileId);
        FileRecord next = new FileRecord(old.id(), old.nodeId(), old.originalFilename(), old.fileType(),
                old.currentVersion(), true, old.createdAt(), Instant.now().toString());
        files.put(fileId, next);
        saveFiles();
    }

    public synchronized void deleteNode(int nodeId) throws IOException {
        List<Integer> toDelete = new ArrayList<>();
        collectDescendants(nodeId, toDelete);
        toDelete.add(nodeId);

        for (int id : toDelete) {
            nodes.remove(id);
        }

        // Also mark any files attached to these nodes as deleted
        for (FileRecord file : new ArrayList<>(files.values())) {
            if (toDelete.contains(file.nodeId())) {
                markFileDeleted(file.id());
            }
        }

        saveNodes();
    }

    private void collectDescendants(int parentId, List<Integer> list) {
        for (VaultNode node : nodes.values()) {
            if (node.parentId() == parentId) {
                list.add(node.id());
                collectDescendants(node.id(), list);
            }
        }
    }

    public synchronized String childrenJson(int parentId) {
        StringBuilder json = new StringBuilder();
        json.append("{\"parentId\":").append(parentId).append(",\"nodes\":[");
        boolean first = true;
        for (VaultNode node : nodes.values()) {
            if (node.parentId() == parentId) {
                if (!first) {
                    json.append(',');
                }
                appendNodeJson(json, node);
                first = false;
            }
        }
        json.append("],\"files\":[");
        first = true;
        for (FileRecord file : files.values()) {
            VaultNode node = nodes.get(file.nodeId());
            if (!file.deleted() && node != null && node.parentId() == parentId) {
                if (!first) {
                    json.append(',');
                }
                appendFileJson(json, file);
                first = false;
            }
        }
        json.append("]}");
        return json.toString();
    }

    public synchronized List<VaultNode> allNodes() {
        return new ArrayList<>(nodes.values());
    }

    public synchronized VaultNode getNode(int nodeId) {
        return nodes.get(nodeId);
    }

    public synchronized List<FileRecord> allFiles() {
        return files.values().stream().filter(file -> !file.deleted()).toList();
    }

    public synchronized List<FileVersion> allVersions() {
        return new ArrayList<>(versions);
    }

    private FileRecord requiredFile(int fileId) {
        FileRecord file = files.get(fileId);
        if (file == null) {
            throw new IllegalArgumentException("File not found: " + fileId);
        }
        return file;
    }

    private void appendNodeJson(StringBuilder json, VaultNode node) {
        json.append("{\"id\":").append(node.id())
                .append(",\"parentId\":").append(node.parentId())
                .append(",\"type\":\"").append(Json.escape(node.type()))
                .append("\",\"title\":\"").append(Json.escape(node.title()))
                .append("\",\"updatedAt\":\"").append(Json.escape(node.updatedAt()))
                .append("\"}");
    }

    private void appendFileJson(StringBuilder json, FileRecord file) {
        json.append("{\"id\":").append(file.id())
                .append(",\"nodeId\":").append(file.nodeId())
                .append(",\"filename\":\"").append(Json.escape(file.originalFilename()))
                .append("\",\"type\":\"").append(Json.escape(file.fileType()))
                .append("\",\"currentVersion\":").append(file.currentVersion())
                .append(",\"updatedAt\":\"").append(Json.escape(file.updatedAt()))
                .append("\"}");
    }

    private void loadNodes() throws IOException {
        if (!Files.exists(nodesPath)) {
            return;
        }
        for (String line : Files.readAllLines(nodesPath, StandardCharsets.UTF_8)) {
            if (line.isBlank()) {
                continue;
            }
            String[] parts = line.split("\t", -1);
            String color = parts.length > 6 ? parts[6] : "";
            String shape = parts.length > 7 ? parts[7] : "circle";
            boolean important = parts.length > 8 && Boolean.parseBoolean(parts[8]);
            boolean favorite = parts.length > 9 && Boolean.parseBoolean(parts[9]);
            String shortcut = parts.length > 10 ? parts[10] : "";

            VaultNode node = new VaultNode(
                    Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1]),
                    parts[2],
                    parts[3],
                    parts[4],
                    parts[5],
                    color,
                    shape,
                    important,
                    favorite,
                    shortcut
            );
            nodes.put(node.id(), node);
            nextNodeId = Math.max(nextNodeId, node.id() + 1);
        }
    }

    private void loadFiles() throws IOException {
        if (!Files.exists(filesPath)) {
            return;
        }
        for (String line : Files.readAllLines(filesPath, StandardCharsets.UTF_8)) {
            if (line.isBlank()) {
                continue;
            }
            String[] parts = line.split("\t", -1);
            FileRecord file = new FileRecord(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
                    parts[2], parts[3], Integer.parseInt(parts[4]), Boolean.parseBoolean(parts[5]), parts[6], parts[7]);
            files.put(file.id(), file);
            nextFileId = Math.max(nextFileId, file.id() + 1);
        }
    }

    private void loadVersions() throws IOException {
        if (!Files.exists(versionsPath)) {
            return;
        }
        for (String line : Files.readAllLines(versionsPath, StandardCharsets.UTF_8)) {
            if (line.isBlank()) {
                continue;
            }
            String[] parts = line.split("\t", -1);
            FileVersion version = new FileVersion(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]), parts[3], Long.parseLong(parts[4]), Long.parseLong(parts[5]),
                    parts[6], parts[7]);
            versions.add(version);
            nextVersionId = Math.max(nextVersionId, version.id() + 1);
        }
    }

    private void saveNodes() throws IOException {
        List<String> lines = nodes.values().stream()
                .map(node -> String.join("\t",
                        String.valueOf(node.id()),
                        String.valueOf(node.parentId()),
                        node.type(),
                        node.title(),
                        node.createdAt(),
                        node.updatedAt(),
                        node.color() == null ? "" : node.color(),
                        node.shape() == null ? "circle" : node.shape(),
                        String.valueOf(node.important()),
                        String.valueOf(node.favorite()),
                        node.shortcut() == null ? "" : node.shortcut()
                ))
                .toList();
        Files.write(nodesPath, lines, StandardCharsets.UTF_8);
    }

    private void saveFiles() throws IOException {
        List<String> lines = files.values().stream()
                .map(file -> String.join("\t", String.valueOf(file.id()), String.valueOf(file.nodeId()),
                        file.originalFilename(), file.fileType(), String.valueOf(file.currentVersion()),
                        String.valueOf(file.deleted()), file.createdAt(), file.updatedAt()))
                .toList();
        Files.write(filesPath, lines, StandardCharsets.UTF_8);
    }

    private void saveVersions() throws IOException {
        List<String> lines = versions.stream()
                .map(version -> String.join("\t", String.valueOf(version.id()), String.valueOf(version.fileId()),
                        String.valueOf(version.versionNumber()), version.storagePath(), String.valueOf(version.originalSize()),
                        String.valueOf(version.compressedSize()), version.compressionType(), version.createdAt()))
                .toList();
        Files.write(versionsPath, lines, StandardCharsets.UTF_8);
    }
}
