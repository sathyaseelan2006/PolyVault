package com.polyvault.compression;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class GzipCompressionStrategy implements CompressionStrategy {
    @Override
    public String name() {
        return "gzip";
    }

    @Override
    public OutputStream compress(OutputStream output) throws IOException {
        return new GZIPOutputStream(output);
    }

    @Override
    public InputStream decompress(InputStream input) throws IOException {
        return new GZIPInputStream(input);
    }
}
