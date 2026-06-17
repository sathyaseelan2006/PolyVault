package com.polyvault.compression;

public final class CompressionFactory {
    private CompressionFactory() {
    }

    public static CompressionStrategy byName(String name) {
        if ("none".equalsIgnoreCase(name)) {
            return new NoCompressionStrategy();
        }
        return new GzipCompressionStrategy();
    }
}
