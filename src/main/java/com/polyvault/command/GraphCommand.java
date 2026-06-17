package com.polyvault.command;

import com.polyvault.graph.GraphService;
import com.polyvault.metadata.MetadataStore;
import com.polyvault.protocol.ResponseWriter;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class GraphCommand implements Command {
    private final MetadataStore metadataStore;

    public GraphCommand(MetadataStore metadataStore) {
        this.metadataStore = metadataStore;
    }

    @Override
    public void execute(InputStream input, OutputStream output) throws Exception {
        String json = new GraphService(metadataStore).graphJson();
        ResponseWriter.body(output, "application/json", json.getBytes(StandardCharsets.UTF_8));
    }
}
