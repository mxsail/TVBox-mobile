package com.tvbox.sync;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.util.Map;

public class OkHttpSyncHttp implements SyncHttp {
    private final OkHttpClient client;

    public OkHttpSyncHttp(OkHttpClient client) {
        this.client = client;
    }

    @Override
    public SyncHttpResponse execute(SyncHttpRequest req) throws IOException {
        Request.Builder builder = new Request.Builder().url(req.url);
        if (req.headers != null) {
            for (Map.Entry<String, String> entry : req.headers.entrySet()) {
                builder.addHeader(entry.getKey(), entry.getValue());
            }
        }
        RequestBody body = null;
        if (req.body != null) {
            body = RequestBody.create(req.body);
        } else if ("PUT".equalsIgnoreCase(req.method) || "POST".equalsIgnoreCase(req.method) || "PATCH".equalsIgnoreCase(req.method)) {
            body = RequestBody.create(new byte[0]);
        }
        builder.method(req.method, body);

        try (Response response = client.newCall(builder.build()).execute()) {
            byte[] responseBytes = response.body() != null ? response.body().bytes() : new byte[0];
            SyncHttpResponse syncRes = new SyncHttpResponse(response.code(), responseBytes);
            for (String name : response.headers().names()) {
                syncRes.headers.put(name, response.header(name));
            }
            return syncRes;
        }
    }
}
