package com.tvbox.sync;

import java.util.HashMap;
import java.util.Map;

public class SyncHttpResponse {
    public int statusCode;
    public Map<String, String> headers = new HashMap<String, String>();
    public byte[] body;

    public SyncHttpResponse() {
    }

    public SyncHttpResponse(int statusCode, byte[] body) {
        this.statusCode = statusCode;
        this.body = body;
    }

    public String getHeader(String name) {
        if (headers == null || name == null) return null;
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (name.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    public String getBodyAsString() {
        if (body == null) return "";
        try {
            return new String(body, "UTF-8");
        } catch (Exception e) {
            return new String(body);
        }
    }
}
