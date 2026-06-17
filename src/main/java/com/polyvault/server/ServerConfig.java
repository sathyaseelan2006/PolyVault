package com.polyvault.server;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class ServerConfig {
    private static final ServerConfig INSTANCE = new ServerConfig();

    private int socketPort = 5050;
    private int httpPort = 8080;
    private Path dataDirectory = Path.of("data");
    private Path storageDirectory = dataDirectory.resolve("storage");
    private Path metadataDirectory = dataDirectory.resolve("metadata");
    private String firebaseApiKey = "";
    private String firebaseProjectId = "";

    private ServerConfig() {
        loadProperties();
    }

    private void loadProperties() {
        Path propPath = Path.of("config.properties");
        if (Files.exists(propPath)) {
            Properties props = new Properties();
            try (InputStream in = Files.newInputStream(propPath)) {
                props.load(in);
                this.socketPort = Integer.parseInt(props.getProperty("socket.port", "5050"));
                this.httpPort = Integer.parseInt(props.getProperty("server.port", "8080"));
                this.dataDirectory = Path.of(props.getProperty("data.directory", "data"));
                this.storageDirectory = Path.of(props.getProperty("storage.directory", "data/storage"));
                this.metadataDirectory = Path.of(props.getProperty("metadata.directory", "data/metadata"));
                this.firebaseApiKey = props.getProperty("firebase.api.key", "");
                this.firebaseProjectId = props.getProperty("firebase.project.id", "");
            } catch (Exception e) {
                System.err.println("Error loading config.properties, using defaults: " + e.getMessage());
            }
        }
    }

    public static ServerConfig getInstance() {
        return INSTANCE;
    }

    public int socketPort() {
        return socketPort;
    }

    public int httpPort() {
        return httpPort;
    }

    public Path storageDirectory() {
        return storageDirectory;
    }

    public Path metadataDirectory() {
        return metadataDirectory;
    }

    public String firebaseApiKey() {
        return firebaseApiKey;
    }

    public String firebaseProjectId() {
        return firebaseProjectId;
    }
}
