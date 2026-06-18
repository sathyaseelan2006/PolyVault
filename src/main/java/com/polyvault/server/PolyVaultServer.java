package com.polyvault.server;

import com.polyvault.graph.GraphApiServer;
import com.polyvault.metadata.MetadataStore;
import com.polyvault.metadata.UserStore;
import com.polyvault.metadata.ShareStore;
import com.polyvault.metadata.InviteStore;
import com.polyvault.storage.StorageService;

import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PolyVaultServer {
    private final ServerConfig config = ServerConfig.getInstance();
    private final ExecutorService pool = Executors.newFixedThreadPool(12);

    public void start() throws Exception {
        Files.createDirectories(config.storageDirectory());
        Files.createDirectories(config.metadataDirectory());

        MetadataStore metadataStore = new MetadataStore(config.metadataDirectory());
        metadataStore.initialize();
        
        UserStore userStore = new UserStore(config.metadataDirectory());
        userStore.initialize();

        ShareStore shareStore = new ShareStore(config.metadataDirectory().getParent());
        shareStore.initialize();

        InviteStore inviteStore = new InviteStore(config.metadataDirectory().getParent());
        inviteStore.initialize();

        StorageService storageService = new StorageService(config.storageDirectory(), metadataStore);
        new GraphApiServer(config.httpPort(), metadataStore, storageService, userStore, shareStore, inviteStore).start();

        try (ServerSocket serverSocket = new ServerSocket(config.socketPort())) {
            System.out.println("PolyVault socket server listening on port " + config.socketPort());
            while (true) {
                Socket socket = serverSocket.accept();
                pool.submit(new ClientSession(socket, metadataStore, storageService));
            }
        }
    }
}
