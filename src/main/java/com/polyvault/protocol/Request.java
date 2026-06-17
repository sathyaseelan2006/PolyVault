package com.polyvault.protocol;

import java.util.Map;

public record Request(String command, Map<String, String> params) {
    public String required(String key) {
        String value = params.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required parameter: " + key);
        }
        return value;
    }

    public String optional(String key, String fallback) {
        return params.getOrDefault(key, fallback);
    }

    public long longParam(String key) {
        return Long.parseLong(required(key));
    }

    public int intParam(String key) {
        return Integer.parseInt(required(key));
    }
}
