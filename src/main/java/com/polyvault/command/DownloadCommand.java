package com.polyvault.command;

import com.polyvault.metadata.FileVersion;
import com.polyvault.metadata.MetadataStore;
import com.polyvault.protocol.Request;
import com.polyvault.storage.StorageService;

import java.io.InputStream;
import java.io.OutputStream;

public class DownloadCommand implements Command {
    private final Request request;
    private final MetadataStore metadataStore;
    private final StorageService storageService;

    public DownloadCommand(Request request, MetadataStore metadataStore, StorageService storageService) {
        this.request = request;
        this.metadataStore = metadataStore;
        this.storageService = storageService;
    }

    @Override
    public void execute(InputStream input, OutputStream output) throws Exception {
        int fileId = request.intParam("fileId");
        String version = request.optional("version", "latest");
        FileVersion fileVersion = "latest".equalsIgnoreCase(version)
                ? metadataStore.latestVersion(fileId)
                : metadataStore.version(fileId, Integer.parseInt(version));
        storageService.writeDecompressed(fileVersion, output);
    }
}
