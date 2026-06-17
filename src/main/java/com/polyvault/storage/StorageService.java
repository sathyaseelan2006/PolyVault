package com.polyvault.storage;

import com.polyvault.compression.CompressionFactory;
import com.polyvault.compression.CompressionStrategy;
import com.polyvault.metadata.FileRecord;
import com.polyvault.metadata.FileVersion;
import com.polyvault.metadata.MetadataStore;
import com.polyvault.metadata.VaultNode;
import com.polyvault.protocol.ResponseWriter;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class StorageService {
    private static final int BUFFER_SIZE = 8192;

    private final Path storageDirectory;
    private final MetadataStore metadataStore;
    private final CompressionStrategy compressionStrategy = CompressionFactory.byName("gzip");

    public StorageService(Path storageDirectory, MetadataStore metadataStore) {
        this.storageDirectory = storageDirectory;
        this.metadataStore = metadataStore;
    }

    public StoredFile store(int parentId, String filename, String title, String fileType, long size, InputStream input) throws IOException {
        VaultNode node = metadataStore.createNode(parentId, "FILE", title);
        FileRecord file = metadataStore.createFile(node.id(), filename, fileType);
        int versionNumber = metadataStore.nextVersionNumber(file.id());

        Path fileDirectory = storageDirectory.resolve("file-" + file.id());
        Files.createDirectories(fileDirectory);
        Path versionPath = fileDirectory.resolve("v" + versionNumber + ".gz");

        long compressedSize;
        try (OutputStream raw = Files.newOutputStream(versionPath);
             CountingOutputStream counted = new CountingOutputStream(raw);
             OutputStream compressed = compressionStrategy.compress(counted)) {
            copyExact(input, compressed, size);
            compressedSize = counted.getByteCount();
        }

        metadataStore.addVersion(file.id(), versionNumber, storageDirectory.relativize(versionPath).toString(),
                size, compressedSize, compressionStrategy.name());
        return new StoredFile(file.id(), node.id(), versionNumber);
    }

    public void writeDecompressed(FileVersion version, OutputStream output) throws IOException {
        ResponseWriter.okLine(output, "size=" + version.originalSize() + " fileId=" + version.fileId()
                + " version=" + version.versionNumber());
        writeDecompressedBody(version, output);
    }

    public void writeDecompressedBody(FileVersion version, OutputStream output) throws IOException {
        Path path = storageDirectory.resolve(version.storagePath()).normalize().toAbsolutePath();
        Path storageDirAbs = storageDirectory.toAbsolutePath().normalize();
        if (!path.startsWith(storageDirAbs)) {
            throw new SecurityException("Directory traversal attempt detected: " + version.storagePath());
        }
        CompressionStrategy strategy = CompressionFactory.byName(version.compressionType());
        try (InputStream raw = Files.newInputStream(path);
             InputStream decompressed = strategy.decompress(raw)) {
            decompressed.transferTo(output);
        }
    }

    private void copyExact(InputStream input, OutputStream output, long size) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        long remaining = size;
        while (remaining > 0) {
            int read = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
            if (read == -1) {
                throw new IOException("Upload ended before expected size");
            }
            output.write(buffer, 0, read);
            remaining -= read;
        }
    }

    private static class CountingOutputStream extends OutputStream {
        private final OutputStream delegate;
        private long byteCount;

        private CountingOutputStream(OutputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public void write(int b) throws IOException {
            delegate.write(b);
            byteCount++;
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            delegate.write(b, off, len);
            byteCount += len;
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }

        long getByteCount() {
            return byteCount;
        }
    }
}
