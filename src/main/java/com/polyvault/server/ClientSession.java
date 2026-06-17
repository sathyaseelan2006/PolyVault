package com.polyvault.server;

import com.polyvault.command.Command;
import com.polyvault.command.CommandFactory;
import com.polyvault.metadata.MetadataStore;
import com.polyvault.protocol.ProtocolParser;
import com.polyvault.protocol.Request;
import com.polyvault.storage.StorageService;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.net.Socket;

public class ClientSession implements Runnable {
    private final Socket socket;
    private final MetadataStore metadataStore;
    private final StorageService storageService;

    public ClientSession(Socket socket, MetadataStore metadataStore, StorageService storageService) {
        this.socket = socket;
        this.metadataStore = metadataStore;
        this.storageService = storageService;
    }

    @Override
    public void run() {
        try (socket;
             BufferedInputStream input = new BufferedInputStream(socket.getInputStream());
             BufferedOutputStream output = new BufferedOutputStream(socket.getOutputStream())) {
            Request request = ProtocolParser.readRequest(input);
            Command command = CommandFactory.create(request, metadataStore, storageService);
            command.execute(input, output);
            output.flush();
        } catch (Exception e) {
            try {
                socket.getOutputStream().write(("ERR message=\"" + e.getMessage() + "\"\n").getBytes());
            } catch (Exception ignored) {
            }
        }
    }
}
