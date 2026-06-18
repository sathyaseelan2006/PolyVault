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
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

public class GraphService {
    private final MetadataStore metadataStore;

    public record SharedMetadataStoreInfo(int shareIndex, MetadataStore store, int sharedWorkspaceNodeId, String ownerEmail) {}

    public GraphService(MetadataStore metadataStore) {
        this.metadataStore = metadataStore;
    }

    public String graphJson() {
        return combinedGraphJson(new ArrayList<>());
    }

    public String combinedGraphJson(List<SharedMetadataStoreInfo> sharedStores) {
        Map<Integer, FileRecord> filesByNode = metadataStore.allFiles().stream()
                .collect(Collectors.toMap(FileRecord::nodeId, file -> file));
        Map<Integer, FileVersion> latestVersions = metadataStore.allVersions().stream()
                .collect(Collectors.groupingBy(FileVersion::fileId,
                        Collectors.collectingAndThen(Collectors.maxBy(Comparator.comparingInt(FileVersion::versionNumber)),
                                optional -> optional.orElse(null))));

        StringBuilder json = new StringBuilder();
        json.append("{\"nodes\":[");
        appendRoot(json, sharedStores);
        
        // Append local nodes
        for (VaultNode node : metadataStore.allNodes()) {
            json.append(',');
            appendNode(json, node, filesByNode.get(node.id()), latestVersions, 0, -1, false, null);
        }

        // Append shared nodes
        for (SharedMetadataStoreInfo info : sharedStores) {
            int offset = info.shareIndex() * 10000000;
            MetadataStore store = info.store();
            VaultNode sharedRoot = store.getNode(info.sharedWorkspaceNodeId());
            if (sharedRoot == null) continue;

            Map<Integer, FileRecord> sharedFilesByNode = store.allFiles().stream()
                    .collect(Collectors.toMap(FileRecord::nodeId, file -> file));
            Map<Integer, FileVersion> sharedLatestVersions = store.allVersions().stream()
                    .collect(Collectors.groupingBy(FileVersion::fileId,
                            Collectors.collectingAndThen(Collectors.maxBy(Comparator.comparingInt(FileVersion::versionNumber)),
                                    optional -> optional.orElse(null))));

            // Shared Root Folder
            json.append(',');
            appendNode(json, sharedRoot, sharedFilesByNode.get(sharedRoot.id()), sharedLatestVersions, offset, 0, true, info.ownerEmail());

            // Descendants
            List<VaultNode> descendants = new ArrayList<>();
            collectDescendants(info.sharedWorkspaceNodeId(), store.allNodes(), descendants);
            for (VaultNode node : descendants) {
                json.append(',');
                appendNode(json, node, sharedFilesByNode.get(node.id()), sharedLatestVersions, offset, -1, true, info.ownerEmail());
            }
        }

        json.append("],\"edges\":[");
        boolean first = true;

        // Edges for local nodes
        for (VaultNode node : metadataStore.allNodes()) {
            if (!first) {
                json.append(',');
            }
            String source = node.parentId() == 0 ? "vault-root" : "node-" + node.parentId();
            json.append("{\"source\":\"").append(source).append("\",\"target\":\"node-").append(node.id())
                    .append("\",\"type\":\"contains\"}");
            first = false;
        }

        // Edges for shared nodes
        for (SharedMetadataStoreInfo info : sharedStores) {
            int offset = info.shareIndex() * 10000000;
            MetadataStore store = info.store();
            VaultNode sharedRoot = store.getNode(info.sharedWorkspaceNodeId());
            if (sharedRoot == null) continue;

            if (!first) json.append(',');
            json.append("{\"source\":\"vault-root\",\"target\":\"node-").append(sharedRoot.id() + offset)
                    .append("\",\"type\":\"contains\"}");
            first = false;

            List<VaultNode> descendants = new ArrayList<>();
            collectDescendants(info.sharedWorkspaceNodeId(), store.allNodes(), descendants);
            for (VaultNode node : descendants) {
                if (!first) json.append(',');
                String source = node.parentId() == info.sharedWorkspaceNodeId()
                        ? "node-" + (sharedRoot.id() + offset)
                        : "node-" + (node.parentId() + offset);
                json.append("{\"source\":\"").append(source).append("\",\"target\":\"node-").append(node.id() + offset)
                        .append("\",\"type\":\"contains\"}");
                first = false;
            }
        }

        json.append("]}");
        return json.toString();
    }

    private void appendRoot(StringBuilder json, List<SharedMetadataStoreInfo> sharedStores) {
        int count = metadataStore.allNodes().size();
        for (SharedMetadataStoreInfo info : sharedStores) {
            VaultNode sharedRoot = info.store().getNode(info.sharedWorkspaceNodeId());
            if (sharedRoot != null) {
                count++;
                List<VaultNode> descendants = new ArrayList<>();
                collectDescendants(info.sharedWorkspaceNodeId(), info.store().allNodes(), descendants);
                count += descendants.size();
            }
        }
        json.append("{\"id\":\"vault-root\",\"label\":\"My Vault\",\"type\":\"root\",\"subtitle\":\"")
                .append(count).append(" nodes\",\"size\":42,\"activityScore\":1.0,\"recency\":\"hot\",\"color\":\"\",\"shape\":\"circle\",\"important\":false,\"favorite\":false}");
    }

    private void appendNode(StringBuilder json, VaultNode node, FileRecord file, Map<Integer, FileVersion> latestVersions,
                            int idOffset, int parentOverride, boolean readOnly, String ownerEmail) {
        double activity = activityScore(node.updatedAt());
        int displayId = node.id() + idOffset;
        int displayParentId = parentOverride >= 0 ? parentOverride : (node.parentId() == 0 ? 0 : node.parentId() + idOffset);

        json.append("{\"id\":\"node-").append(displayId)
                .append("\",\"nodeId\":").append(displayId)
                .append(",\"label\":\"").append(Json.escape(node.title()))
                .append("\",\"type\":\"").append(Json.escape(node.type().toLowerCase()))
                .append("\",\"parentId\":").append(displayParentId)
                .append(",\"size\":").append(sizeFor(node.type(), activity))
                .append(",\"activityScore\":").append(String.format(java.util.Locale.ROOT, "%.2f", activity))
                .append(",\"recency\":\"").append(recency(activity))
                .append("\",\"color\":\"").append(Json.escape(node.color() == null ? "" : node.color()))
                .append("\",\"shape\":\"").append(Json.escape(node.shape() == null ? "circle" : node.shape()))
                .append("\",\"important\":").append(node.important())
                .append(",\"favorite\":").append(node.favorite())
                .append(",\"shortcut\":\"").append(Json.escape(node.shortcut() == null ? "" : node.shortcut()))
                .append("\",\"updatedAt\":\"").append(Json.escape(node.updatedAt())).append("\"")
                .append(",\"readOnly\":").append(readOnly);

        if (ownerEmail != null && !ownerEmail.isEmpty()) {
            json.append(",\"ownerEmail\":\"").append(Json.escape(ownerEmail)).append("\"");
        }

        if (file != null) {
            FileVersion version = latestVersions.get(file.id());
            int displayFileId = file.id() + idOffset;
            json.append(",\"fileId\":").append(displayFileId)
                    .append(",\"filename\":\"").append(Json.escape(file.originalFilename()))
                    .append("\",\"currentVersion\":").append(file.currentVersion());
            if (version != null) {
                json.append(",\"originalSize\":").append(version.originalSize());
            }
        }
        json.append('}');
    }

    private void collectDescendants(int parentId, List<VaultNode> allNodes, List<VaultNode> out) {
        for (VaultNode node : allNodes) {
            if (node.parentId() == parentId) {
                out.add(node);
                collectDescendants(node.id(), allNodes, out);
            }
        }
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
