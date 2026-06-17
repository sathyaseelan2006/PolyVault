package com.polyvault.protocol;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public final class ResponseWriter {
    private ResponseWriter() {
    }

    public static void ok(OutputStream output, String message) throws IOException {
        output.write(("OK message=\"" + escape(message) + "\"\n").getBytes(StandardCharsets.UTF_8));
    }

    public static void okLine(OutputStream output, String line) throws IOException {
        output.write(("OK " + line + "\n").getBytes(StandardCharsets.UTF_8));
    }

    public static void body(OutputStream output, String contentType, byte[] body) throws IOException {
        output.write(("OK size=" + body.length + " contentType=" + contentType + "\n\n").getBytes(StandardCharsets.UTF_8));
        output.write(body);
    }

    public static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
