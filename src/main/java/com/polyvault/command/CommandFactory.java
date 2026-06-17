package com.polyvault.command;

import com.polyvault.metadata.MetadataStore;
import com.polyvault.protocol.Request;
import com.polyvault.storage.StorageService;

public final class CommandFactory {
    private CommandFactory() {
    }

    public static Command create(Request request, MetadataStore metadataStore, StorageService storageService) {
        return switch (request.command()) {
            case "CREATE_NODE" -> new CreateNodeCommand(request, metadataStore);
            case "UPLOAD" -> new UploadCommand(request, storageService);
            case "DOWNLOAD" -> new DownloadCommand(request, metadataStore, storageService);
            case "LIST" -> new ListCommand(request, metadataStore);
            case "DELETE" -> new DeleteCommand(request, metadataStore);
            case "GRAPH" -> new GraphCommand(metadataStore);
            default -> throw new IllegalArgumentException("Unknown command: " + request.command());
        };
    }
}
