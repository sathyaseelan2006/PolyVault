package com.polyvault.compression;

import java.io.InputStream;
import java.io.OutputStream;

public class NoCompressionStrategy implements CompressionStrategy {
    @Override
    public String name() {
        return "none";
    }

    @Override
    public OutputStream compress(OutputStream output) {
        return output;
    }

    @Override
    public InputStream decompress(InputStream input) {
        return input;
    }
}
