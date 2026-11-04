package com.tvbox.sync;

import java.util.HashMap;
import java.util.Map;

public class SyncHttpRequest {
    public String method;
    public String url;
    public Map<String, String> headers = new HashMap<String, String>();
    public byte[] body;

    public SyncHttpRequest() {
    }

    public SyncHttpRequest(String method, String url) {
        this.method = method;
        this.url = url;
    }
}
