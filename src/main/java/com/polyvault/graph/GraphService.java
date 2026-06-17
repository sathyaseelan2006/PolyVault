package com.polyvault.graph;

import com.polyvault.metadata.FileRecord;
import com.polyvault.metadata.FileVersion;
import com.polyvault.metadata.MetadataStore;
import com.polyvault.metadata.VaultNode;
import com.polyvault.util.Json;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;

public class GraphService {
    private final MetadataStore metadataStore;

    public GraphService(MetadataStore metadataStore) {
        this.metadataStore = metadataStore;
    }

    public String graphJson() {
        Map<Integer, FileRecord> filesByNode = metadataStore.allFiles().stream()
                .collect(Collectors.toMap(FileRecord::nodeId, file -> file));
        Map<Integer, FileVersion> latestVersions = metadataStore.allVersions().stream()
                .collect(Collectors.groupingBy(FileVersion::fileId,
                        Collectors.collectingAndThen(Collectors.maxBy(Comparator.comparingInt(FileVersion::versionNumber)),
                                optional -> optional.orElse(null))));

        StringBuilder json = new StringBuilder();
        json.append("{\"nodes\":[");
        appendRoot(json);
        for (VaultNode node : metadataStore.allNodes()) {
            json.append(',');
            appendNode(json, node, filesByNode.get(node.id()), latestVersions);
        }
        json.append("],\"edges\":[");
        boolean first = true;
        for (VaultNode node : metadataStore.allNodes()) {
            if (!first) {
                json.append(',');
            }
            String source = node.parentId() == 0 ? "vault-root" : "node-" + node.parentId();
            json.append("{\"source\":\"").append(source).append("\",\"target\":\"node-").append(node.id())
                    .append("\",\"type\":\"contains\"}");
            first = false;
        }
        json.append("]}");
        return json.toString();
    }

    private void appendRoot(StringBuilder json) {
        int count = metadataStore.allNodes().size();
        json.append("{\"id\":\"vault-root\",\"label\":\"My Vault\",\"type\":\"root\",\"subtitle\":\"")
                .append(count).append(" nodes\",\"size\":42,\"activityScore\":1.0,\"recency\":\"hot\",\"color\":\"\",\"shape\":\"circle\",\"important\":false,\"favorite\":false}");
    }

    private void appendNode(StringBuilder json, VaultNode node, FileRecord file, Map<Integer, FileVersion> latestVersions) {
        double activity = activityScore(node.updatedAt());
        json.append("{\"id\":\"node-").append(node.id())
                .append("\",\"nodeId\":").append(node.id())
                .append(",\"label\":\"").append(Json.escape(node.title()))
                .append("\",\"type\":\"").append(Json.escape(node.type().toLowerCase()))
                .append("\",\"parentId\":").append(node.parentId())
                .append(",\"size\":").append(sizeFor(node.type(), activity))
                .append(",\"activityScore\":").append(String.format(java.util.Locale.ROOT, "%.2f", activity))
                .append(",\"recency\":\"").append(recency(activity))
                .append("\",\"color\":\"").append(Json.escape(node.color() == null ? "" : node.color()))
                .append("\",\"shape\":\"").append(Json.escape(node.shape() == null ? "circle" : node.shape()))
                .append("\",\"important\":").append(node.important())
                .append(",\"favorite\":").append(node.favorite())
                .append(",\"shortcut\":\"").append(Json.escape(node.shortcut() == null ? "" : node.shortcut()))
                .append("\",\"updatedAt\":\"").append(Json.escape(node.updatedAt())).append("\"");
        if (file != null) {
            FileVersion version = latestVersions.get(file.id());
            json.append(",\"fileId\":").append(file.id())
                    .append(",\"filename\":\"").append(Json.escape(file.originalFilename()))
                    .append("\",\"currentVersion\":").append(file.currentVersion());
            if (version != null) {
                json.append(",\"originalSize\":").append(version.originalSize());
            }
        }
        json.append('}');
    }

    private int sizeFor(String type, double activity) {
        int base = switch (type.toUpperCase()) {
            case "WORKSPACE" -> 34;
            case "PROJECT" -> 32;
            case "FILE" -> 22;
            default -> 26;
        };
        return base + (int) (activity * 8);
    }

    private double activityScore(String updatedAt) {
        Duration age = Duration.between(Instant.parse(updatedAt), Instant.now());
        long days = Math.max(0, age.toDays());
        if (days == 0) {
            return 1.0;
        }
        return Math.max(0.15, 1.0 - (days / 30.0));
    }

    private String recency(double activity) {
        if (activity > 0.8) {
            return "hot";
        }
        if (activity > 0.45) {
            return "warm";
        }
        return "stale";
    }
}
