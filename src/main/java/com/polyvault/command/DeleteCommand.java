package com.polyvault.command;

import com.polyvault.metadata.MetadataStore;
import com.polyvault.protocol.Request;
import com.polyvault.protocol.ResponseWriter;

import java.io.InputStream;
import java.io.OutputStream;

public class DeleteCommand implements Command {
    private final Request request;
    private final MetadataStore metadataStore;

    public DeleteCommand(Request request, MetadataStore metadataStore) {
        this.request = request;
        this.metadataStore = metadataStore;
    }

    @Override
    public void execute(InputStream input, OutputStream output) throws Exception {
        metadataStore.markFileDeleted(request.intParam("fileId"));
        ResponseWriter.ok(output, "File marked deleted");
    }
}
