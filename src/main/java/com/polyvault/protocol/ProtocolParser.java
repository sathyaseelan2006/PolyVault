package com.polyvault.protocol;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ProtocolParser {
    private ProtocolParser() {
    }

    public static Request readRequest(InputStream input) throws IOException {
        String line = readLine(input);
        if (line == null || line.isBlank()) {
            throw new IOException("Empty request");
        }
        List<String> tokens = Tokenizer.tokenize(line);
        String command = tokens.get(0).toUpperCase();
        Map<String, String> params = new LinkedHashMap<>();
        for (int i = 1; i < tokens.size(); i++) {
            String token = tokens.get(i);
            int index = token.indexOf('=');
            if (index > 0) {
                params.put(token.substring(0, index), token.substring(index + 1));
            }
        }
        return new Request(command, params);
    }

    public static String readLine(InputStream input) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int value;
        while ((value = input.read()) != -1) {
            if (value == '\n') {
                break;
            }
            if (value != '\r') {
                buffer.write(value);
            }
        }
        if (value == -1 && buffer.size() == 0) {
            return null;
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }
}
