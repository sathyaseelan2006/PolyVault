package com.polyvault.client;

import com.polyvault.protocol.ProtocolParser;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class PolyVaultClient {
    private static final String HOST = "localhost";
    private static final int PORT = 5050;

    public void run(String[] args) throws Exception {
        if (args.length == 0) {
            help();
            return;
        }
        switch (args[0].toLowerCase()) {
            case "node" -> createNode(args);
            case "upload" -> upload(args);
            case "download" -> download(args);
            case "list" -> list(args);
            case "graph" -> graph();
            default -> help();
        }
    }

    private void createNode(String[] args) throws IOException {
        if (args.length < 4) {
            System.out.println("Usage: client node <parentId> <type> <title>");
            return;
        }
        sendLine("CREATE_NODE parentId=" + args[1] + " type=" + quote(args[2]) + " title=" + quote(args[3]));
    }

    private void upload(String[] args) throws IOException {
        if (args.length < 3) {
            System.out.println("Usage: client upload <path> <parentId> [title]");
            return;
        }
        Path path = Path.of(args[1]);
        String title = args.length >= 4 ? args[3] : path.getFileName().toString();
        long size = Files.size(path);
        try (Socket socket = new Socket(HOST, PORT);
             BufferedOutputStream output = new BufferedOutputStream(socket.getOutputStream());
             BufferedInputStream input = new BufferedInputStream(socket.getInputStream());
             BufferedInputStream fileInput = new BufferedInputStream(Files.newInputStream(path))) {
            String header = "UPLOAD parentId=" + args[2]
                    + " filename=" + quote(path.getFileName().toString())
                    + " title=" + quote(title)
                    + " type=" + quote(detectType(path))
                    + " size=" + size + "\n";
            output.write(header.getBytes(StandardCharsets.UTF_8));
            fileInput.transferTo(output);
            output.flush();
            System.out.println(ProtocolParser.readLine(input));
        }
    }

    private void download(String[] args) throws IOException {
        if (args.length < 3) {
            System.out.println("Usage: client download <fileId> <outPath> [version]");
            return;
        }
        String version = args.length >= 4 ? args[3] : "latest";
        try (Socket socket = new Socket(HOST, PORT);
             BufferedOutputStream output = new BufferedOutputStream(socket.getOutputStream());
             BufferedInputStream input = new BufferedInputStream(socket.getInputStream())) {
            output.write(("DOWNLOAD fileId=" + args[1] + " version=" + quote(version) + "\n").getBytes(StandardCharsets.UTF_8));
            output.flush();
            String response = ProtocolParser.readLine(input);
            System.out.println(response);
            Files.copy(input, Path.of(args[2]), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void list(String[] args) throws IOException {
        String parentId = args.length >= 2 ? args[1] : "0";
        sendBodyCommand("LIST parentId=" + parentId);
    }

    private void graph() throws IOException {
        sendBodyCommand("GRAPH");
    }

    private void sendLine(String line) throws IOException {
        try (Socket socket = new Socket(HOST, PORT);
             BufferedOutputStream output = new BufferedOutputStream(socket.getOutputStream());
             BufferedInputStream input = new BufferedInputStream(socket.getInputStream())) {
            output.write((line + "\n").getBytes(StandardCharsets.UTF_8));
            output.flush();
            System.out.println(ProtocolParser.readLine(input));
        }
    }

    private void sendBodyCommand(String line) throws IOException {
        try (Socket socket = new Socket(HOST, PORT);
             BufferedOutputStream output = new BufferedOutputStream(socket.getOutputStream());
             BufferedInputStream input = new BufferedInputStream(socket.getInputStream())) {
            output.write((line + "\n").getBytes(StandardCharsets.UTF_8));
            output.flush();
            String header = ProtocolParser.readLine(input);
            ProtocolParser.readLine(input);
            System.out.println(header);
            input.transferTo(System.out);
            System.out.println();
        }
    }

    private String detectType(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        if (name.endsWith(".java") || name.endsWith(".js") || name.endsWith(".py")) {
            return "code";
        }
        if (name.endsWith(".pdf")) {
            return "pdf";
        }
        if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            return "image";
        }
        return "doc";
    }

    private String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private void help() {
        System.out.println("Commands:");
        System.out.println("  client node <parentId> <type> <title>");
        System.out.println("  client upload <path> <parentId> [title]");
        System.out.println("  client download <fileId> <outPath> [version]");
        System.out.println("  client list [parentId]");
        System.out.println("  client graph");
    }
}
