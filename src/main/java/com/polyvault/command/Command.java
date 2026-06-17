package com.polyvault.command;

import java.io.InputStream;
import java.io.OutputStream;

public interface Command {
    void execute(InputStream input, OutputStream output) throws Exception;
}
