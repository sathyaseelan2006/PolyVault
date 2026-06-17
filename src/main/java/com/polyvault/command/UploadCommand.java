package com.polyvault.command;

import com.polyvault.protocol.Request;
import com.polyvault.protocol.ResponseWriter;
import com.polyvault.storage.StoredFile;
import com.polyvault.storage.StorageService;

import java.io.InputStream;
import java.io.OutputStream;

public class UploadCommand implements Command {
    private final Request request;
    private final StorageService storageService;

    public UploadCommand(Request request, StorageService storageService) {
        this.request = request;
        this.storageService = storageService;
    }

    @Override
    public void execute(InputStream input, OutputStream output) throws Exception {
        StoredFile stored = storageService.store(
                Integer.parseInt(request.optional("parentId", "0")),
                request.required("filename"),
                request.optional("title", request.required("filename")),
                request.optional("type", "file"),
                request.longParam("size"),
                input
        );
        ResponseWriter.okLine(output, "fileId=" + stored.fileId() + " nodeId=" + stored.nodeId()
                + " version=" + stored.versionNumber() + " message=\"Upload successful\"");
    }
}
