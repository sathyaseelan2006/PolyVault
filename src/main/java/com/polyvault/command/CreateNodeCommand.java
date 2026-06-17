package com.polyvault.command;

import com.polyvault.metadata.MetadataStore;
import com.polyvault.metadata.VaultNode;
import com.polyvault.protocol.Request;
import com.polyvault.protocol.ResponseWriter;

import java.io.InputStream;
import java.io.OutputStream;

public class CreateNodeCommand implements Command {
    private final Request request;
    private final MetadataStore metadataStore;

    public CreateNodeCommand(Request request, MetadataStore metadataStore) {
        this.request = request;
        this.metadataStore = metadataStore;
    }

    @Override
    public void execute(InputStream input, OutputStream output) throws Exception {
        int parentId = Integer.parseInt(request.optional("parentId", "0"));
        VaultNode node = metadataStore.createNode(parentId, request.required("type"), request.required("title"));
        ResponseWriter.okLine(output, "nodeId=" + node.id() + " message=\"Node created\"");
    }
}
