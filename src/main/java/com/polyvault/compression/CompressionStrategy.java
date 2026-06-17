package com.polyvault.compression;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public interface CompressionStrategy {
    String name();

    OutputStream compress(OutputStream output) throws IOException;

    InputStream decompress(InputStream input) throws IOException;
}
