package com.polyvault.command;

import com.polyvault.metadata.MetadataStore;
import com.polyvault.protocol.Request;
import com.polyvault.protocol.ResponseWriter;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class ListCommand implements Command {
    private final Request request;
    private final MetadataStore metadataStore;

    public ListCommand(Request request, MetadataStore metadataStore) {
        this.request = request;
        this.metadataStore = metadataStore;
    }

    @Override
    public void execute(InputStream input, OutputStream output) throws Exception {
        int parentId = Integer.parseInt(request.optional("parentId", "0"));
        ResponseWriter.body(output, "application/json", metadataStore.childrenJson(parentId).getBytes(StandardCharsets.UTF_8));
    }
}
