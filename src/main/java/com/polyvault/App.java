package com.polyvault;

import com.polyvault.client.PolyVaultClient;
import com.polyvault.server.PolyVaultServer;

public class App {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("Usage: java com.polyvault.App server|client ...");
            return;
        }

        if ("server".equalsIgnoreCase(args[0])) {
            new PolyVaultServer().start();
            return;
        }

        if ("client".equalsIgnoreCase(args[0])) {
            new PolyVaultClient().run(slice(args));
            return;
        }

        System.out.println("Unknown mode: " + args[0]);
    }

    private static String[] slice(String[] args) {
        String[] next = new String[args.length - 1];
        System.arraycopy(args, 1, next, 0, next.length);
        return next;
    }
}
