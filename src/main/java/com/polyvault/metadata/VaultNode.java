package com.polyvault.metadata;

public record VaultNode(
        int id,
        int parentId,
        String type,
        String title,
        String createdAt,
        String updatedAt,
        String color,
        String shape,
        boolean important,
        boolean favorite,
        String shortcut
) {
}
