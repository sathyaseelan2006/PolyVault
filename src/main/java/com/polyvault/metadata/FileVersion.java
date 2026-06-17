package com.polyvault.metadata;

public record FileVersion(
        int id,
        int fileId,
        int versionNumber,
        String storagePath,
        long originalSize,
        long compressedSize,
        String compressionType,
        String createdAt
) {
}
