package com.polyvault.metadata;

public record FileRecord(
        int id,
        int nodeId,
        String originalFilename,
        String fileType,
        int currentVersion,
        boolean deleted,
        String createdAt,
        String updatedAt
) {
}
